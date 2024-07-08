/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.FileActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Date

class HomeViewModel : ViewModel() {

    var lastActivitiesResult = MutableLiveData<LastActivityResult?>()

    private var lastActivityJob: Job? = null

    private var lastActivitiesTime: Long = 0
    private var currentCursor: String? = null
    private var lastActivities = arrayListOf<FileActivity>()
    private var lastMergedActivities = arrayListOf<FileActivity>()

    val needToRestoreFiles get() = lastMergedActivities.isNotEmpty()

    fun loadLastActivities(driveId: Int, forceDownload: Boolean = false) {
        loadLastActivities(driveId, forceDownload, isFirstPage = true)
    }

    fun loadMoreActivities(driveId: Int) {
        currentCursor?.let {
            loadLastActivities(driveId, forceDownload = false, cursor = it)
        }
    }

    fun restoreActivitiesIfNeeded() {
        if (lastMergedActivities.isNotEmpty()) {
            lastActivitiesResult.value = LastActivityResult(
                mergedActivities = lastMergedActivities,
                isComplete = lastActivitiesResult.value?.isComplete == true,
                isFirstPage = true,
            )
        }
    }

    private fun loadLastActivities(
        driveId: Int,
        forceDownload: Boolean = false,
        isFirstPage: Boolean = false,
        cursor: String? = null,
    ) {
        lastActivityJob?.cancel()
        lastActivityJob = viewModelScope.launch(Dispatchers.IO) {
            val lastActivityResult = getLastActivities(driveId, forceDownload, isFirstPage, cursor)
            if (lastActivityJob?.isActive == true) lastActivitiesResult.postValue(lastActivityResult)
        }
    }

    private fun getLastActivities(
        driveId: Int,
        forceDownload: Boolean,
        isFirstPage: Boolean,
        cursor: String?,
    ): LastActivityResult? {

        fun isDownloadIntervalShort(): Boolean = (Date().time - lastActivitiesTime) < DOWNLOAD_INTERVAL

        fun lastActivityResultFromRemote(data: ArrayList<FileActivity>, isComplete: Boolean): LastActivityResult {
            val mergeAndCleanActivities = mergeAndCleanActivities(data)
            lastActivities.addAll(data)
            lastMergedActivities.addAll(mergeAndCleanActivities)
            FileController.storeFileActivities(data)

            return LastActivityResult(
                mergedActivities = mergeAndCleanActivities,
                isComplete = isComplete,
                isFirstPage = isFirstPage
            )
        }

        fun lastActivityResultFromLocal(): LastActivityResult {
            val localActivities = FileController.getActivities()
            val mergeAndCleanActivities = mergeAndCleanActivities(localActivities)
            lastActivities = localActivities
            lastMergedActivities = mergeAndCleanActivities

            return LastActivityResult(mergedActivities = mergeAndCleanActivities, isComplete = true, isFirstPage = true)
        }

        val ignoreDownload = isFirstPage && lastActivitiesTime != 0L && isDownloadIntervalShort() && !forceDownload

        if (ignoreDownload) {
            return LastActivityResult(mergedActivities = lastMergedActivities, isComplete = false, isFirstPage = true)
        }

        lastActivitiesTime = Date().time

        val apiResponse = ApiRepository.getLastActivities(driveId, cursor)
        lastActivityJob?.ensureActive()
        val apiResponseData = apiResponse.data
        currentCursor = apiResponse.cursor

        if (apiResponse.isSuccess() && isFirstPage) {
            FileController.removeOrphanAndActivityFiles()
            lastActivities = arrayListOf()
            lastMergedActivities = arrayListOf()
        }

        return when {
            apiResponseData != null -> lastActivityResultFromRemote(apiResponseData, isComplete = apiResponse.cursor == null)
            !apiResponse.isSuccess() && isFirstPage -> lastActivityResultFromLocal()
            else -> null
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

    data class LastActivityResult(
        val mergedActivities: ArrayList<FileActivity>,
        val isComplete: Boolean,
        val isFirstPage: Boolean,
    )

    companion object {
        const val DOWNLOAD_INTERVAL: Long = 1 * 60 * 1000 // 1min (ms)
    }
}
