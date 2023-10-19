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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.isLastPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class RecentChangesViewModel : ViewModel() {

    private var getRecentChangesJob = Job()

    var currentPage = 1

    fun getRecentChanges(driveId: Int): LiveData<FileListFragment.FolderFilesResult?> {
        getRecentChangesJob.cancel()
        getRecentChangesJob = Job()

        return liveData(Dispatchers.IO + getRecentChangesJob) {
            val apiResponse = ApiRepository.getLastModifiedFiles(driveId, currentPage)
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { data ->
                    val isComplete = apiResponse.isLastPage()
                    val isFirstPage = currentPage == 1
                    FileController.storeRecentChanges(data, isFirstPage)
                    emit(FileListFragment.FolderFilesResult(files = data, isComplete = isComplete, isFirstPage = isFirstPage))
                }
            } else {
                emit(
                    FileListFragment.FolderFilesResult(
                        files = FileController.getRecentChanges(),
                        isComplete = true,
                        isFirstPage = true,
                    )
                )
            }
        }
    }
}
