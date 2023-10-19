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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.isLastPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RecentChangesViewModel : ViewModel() {

    private var getRecentChangesJob: Job? = null

    var currentPage = 1
    var currentCursor: String? = null

    var recentChangesResults = MutableLiveData<FileListFragment.FolderFilesResult?>()
    var isNewSort = false

    fun loadRecentChanges() {
        getRecentChangesJob?.cancel()
        getRecentChangesJob = viewModelScope.launch(Dispatchers.IO) {
            recentChangesResults.postValue(getRecentChanges(cursor = null, isFirstPage = true))
        }
    }

    fun loadNextPage() = viewModelScope.launch(Dispatchers.IO) {
        currentCursor?.let {
            recentChangesResults.postValue(getRecentChanges(cursor = it, isFirstPage = false))
        }
    }

    private fun getRecentChanges(cursor: String?, isFirstPage: Boolean): FileListFragment.FolderFilesResult? {

        val apiResponse = ApiRepository.getLastModifiedFiles(AccountUtils.currentDriveId, cursor)
        return if (apiResponse.isSuccess()) {
            apiResponse.data?.let { data ->
                val isComplete = apiResponse.isLastPage()
                currentCursor = apiResponse.cursor
                FileController.storeRecentChanges(data, isFirstPage)
                FileListFragment.FolderFilesResult(files = data, isComplete = isComplete, isFirstPage = isFirstPage)
            }
        } else {
            FileListFragment.FolderFilesResult(
                files = FileController.getRecentChanges(),
                isComplete = true,
                isFirstPage = true,
            )
        }
    }
}
