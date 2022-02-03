/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.ui.home

import androidx.collection.arrayMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponse.Status.SUCCESS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*

class HomeViewModel : ViewModel() {
    private var lastActivityJob = Job()

    var lastActivityPage = 1

    var lastActivityLastPage = 1
    private var lastActivitiesTime: Long = 0
    private var lastActivities = arrayListOf<FileActivity>()
    private var lastMergedActivities = arrayListOf<FileActivity>()

    fun getLastActivities(
        driveId: Int,
        forceDownload: Boolean = false
    ): LiveData<Pair<ApiResponse<ArrayList<FileActivity>>, ArrayList<FileActivity>>?> {
        lastActivityJob.cancel()
        lastActivityJob = Job()

        val ignoreDownload =
            lastActivityPage == 1 && lastActivitiesTime != 0L && (Date().time - lastActivitiesTime) < DOWNLOAD_INTERVAL && !forceDownload
        return liveData(Dispatchers.IO + lastActivityJob) {
            if (ignoreDownload) {
                lastActivityPage = lastActivityLastPage
                emit(ApiResponse(SUCCESS, lastActivities, page = 1) to lastMergedActivities)
                return@liveData
            }
            lastActivitiesTime = Date().time

            val apiRepository = ApiRepository.getLastActivities(driveId, lastActivityPage)
            val data = apiRepository.data
            if (apiRepository.isSuccess() || (lastActivityPage == 1 && data != null)) {
                if (lastActivityPage == 1) {
                    FileController.removeOrphanAndActivityFiles()
                    lastActivities = arrayListOf()
                    lastMergedActivities = arrayListOf()
                }

                when {
                    data?.isNullOrEmpty() == true -> emit(null)
                    else -> {
                        val mergeAndCleanActivities = mergeAndCleanActivities(data)
                        lastActivities.addAll(data)
                        lastMergedActivities.addAll(mergeAndCleanActivities)
                        FileController.storeFileActivities(data)
                        emit(apiRepository to mergeAndCleanActivities)
                    }
                }
            } else if (lastActivityPage == 1) {
                val localActivities = FileController.getActivities()
                val mergeAndCleanActivities = mergeAndCleanActivities(localActivities)
                lastActivities = localActivities
                lastMergedActivities = mergeAndCleanActivities
                emit(
                    ApiResponse(
                        data = localActivities,
                        page = 1,
                        pages = 1,
                        result = SUCCESS
                    ) to mergeAndCleanActivities
                )
            }
        }
    }

    /**
     * Will merge activities together (only file creation with similar date) and clean them (remove those without user)
     */
    private fun mergeAndCleanActivities(activities: ArrayList<FileActivity>): ArrayList<FileActivity> {
        val resultActivities: ArrayList<FileActivity> = ArrayList()
        val ignoredActivityIds = arrayListOf<Int>()
        activities.forEachIndexed { index, currentActivity ->
            val ignoreEvenActivity = resultActivities.size > 0 && ignoreEventActivities(resultActivities.last(), currentActivity)
            val mergedFilesTemp = arrayMapOf(currentActivity.fileId to currentActivity)
            if (currentActivity.user != null && !ignoredActivityIds.contains(currentActivity.fileId) && !ignoreEvenActivity) {
                var i = index + 1
                while (i < activities.size && currentActivity.createdAt.time - activities[i].createdAt.time <= HomeFragment.MERGE_FILE_ACTIVITY_DELAY) {
                    if (currentActivity.user?.id == activities[i].user?.id &&
                        currentActivity.action == activities[i].action &&
                        currentActivity.file?.type == activities[i].file?.type &&
                        mergedFilesTemp[activities[i].fileId] == null
                    ) {
                        ignoredActivityIds.add(activities[i].fileId)
                        mergedFilesTemp[activities[i].fileId] = activities[i]
                        currentActivity.mergedFileActivities.add(activities[i])
                    }
                    i++
                }
                resultActivities.add(currentActivity)
            }
        }
        return resultActivities
    }

    private fun ignoreEventActivities(previousActivity: FileActivity, currentActivity: FileActivity): Boolean {
        return previousActivity.user?.id == currentActivity.user?.id &&
                previousActivity.action == currentActivity.action &&
                previousActivity.fileId == currentActivity.fileId
    }

    override fun onCleared() {
        lastActivityJob.cancel()
        super.onCleared()
    }

    companion object {
        const val DOWNLOAD_INTERVAL: Long = 1 * 60 * 1000 // 1min (ms)
    }
}
