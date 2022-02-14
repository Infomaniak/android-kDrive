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
package com.infomaniak.drive.ui.menu

import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.home.HomeViewModel.Companion.DOWNLOAD_INTERVAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*

class RecentChangesViewModel : ViewModel() {

    private var getRecentChangesJob = Job()
    private var lastModifiedTime: Long = 0

    fun getRecentChanges(
        driveId: Int,
        onlyFirstPage: Boolean,
        forceDownload: Boolean = false,
    ): LiveData<FileListFragment.FolderFilesResult?> {
        getRecentChangesJob.cancel()
        getRecentChangesJob = Job()
        val ignoreDownload = lastModifiedTime != 0L && (Date().time - lastModifiedTime) < DOWNLOAD_INTERVAL && !forceDownload
        return liveData(Dispatchers.IO + getRecentChangesJob) {
            if (ignoreDownload) {
                emit(FileListFragment.FolderFilesResult(files = FileController.getRecentChanges(), isComplete = true, page = 1))
            } else {
                getRecentChangesRecursive(1, driveId, onlyFirstPage)
            }
        }
    }

    private suspend fun LiveDataScope<FileListFragment.FolderFilesResult?>.getRecentChangesRecursive(
        page: Int,
        driveId: Int,
        onlyFirstPage: Boolean,
    ) {
        val isFirstPage = page == 1
        val apiResponse = ApiRepository.getLastModifiedFiles(driveId, page)
        when {
            apiResponse.isSuccess() -> apiResponse.data?.let { data ->
                if (isFirstPage) emit(FileListFragment.FolderFilesResult(files = data, isComplete = true, page = page))
                if (data.size >= ApiRepository.PER_PAGE && !onlyFirstPage) {
                    getRecentChangesRecursive(page + 1, driveId, onlyFirstPage)
                }
                FileController.storeRecentChanges(data, isFirstPage)
            }
            isFirstPage -> emit(
                FileListFragment.FolderFilesResult(
                    files = FileController.getRecentChanges(),
                    isComplete = true,
                    page = 1,
                )
            )
            else -> emit(null)
        }
    }
}
