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
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TrashViewModel : ViewModel() {

    val selectedFile = MutableLiveData<File>()
    val removeFileId = SingleLiveEvent<Int>()
    var driveTrashResults = MutableLiveData<FolderFilesResult?>()
    var trashedFolderFilesResults = MutableLiveData<FolderFilesResult?>()

    private var getDeletedFilesJob: Job? = null

    fun loadDriveTrash(driveId: Int, order: File.SortType, isNewSort: Boolean) {
        getDeletedFilesJob?.cancel()
        getDeletedFilesJob = viewModelScope.launch(Dispatchers.IO) {
            driveTrashResults.postValue(getDriveTrash(driveId, order, isNewSort))
        }
    }

    fun loadTrashedFolderFiles(file: File, order: File.SortType, isNewSort: Boolean) {
        getDeletedFilesJob?.cancel()
        getDeletedFilesJob = viewModelScope.launch(Dispatchers.IO) {
            trashedFolderFilesResults.postValue(getTrashedFolderFiles(file, order, isNewSort))
        }
    }

    private fun getTrashedFolderFiles(file: File, order: File.SortType, isNewSort: Boolean): FolderFilesResult? {

        tailrec fun recursive(page: Int): FolderFilesResult? {
            val data = ApiRepository.getTrashedFolderFiles(file, order, page).data
            return when {
                data == null -> null
                data.size < ApiRepository.PER_PAGE ->
                    FolderFilesResult(
                        parentFolder = file,
                        files = ArrayList(data),
                        isComplete = true,
                        isFirstPage = page == 1,
                        isNewSort = isNewSort,
                    )
                else -> {
                    FolderFilesResult(
                        parentFolder = file,
                        files = ArrayList(data),
                        isComplete = false,
                        isFirstPage = page == 1,
                        isNewSort = isNewSort,
                    )
                    recursive(page + 1)
                }
            }
        }

        return recursive(1)
    }

    private fun getDriveTrash(driveId: Int, order: File.SortType, isNewSort: Boolean): FolderFilesResult? {

        fun recursive(page: Int): FolderFilesResult? {
            val apiResponse = ApiRepository.getDriveTrash(driveId, order, page)
            return when {
                apiResponse.data.isNullOrEmpty() -> null
                apiResponse.data!!.size < ApiRepository.PER_PAGE ->
                    FolderFilesResult(
                        files = apiResponse.data!!,
                        isComplete = true,
                        isFirstPage = page == 1,
                        isNewSort = isNewSort,
                    )
                else -> {
                    FolderFilesResult(
                        files = apiResponse.data!!,
                        isComplete = false,
                        isFirstPage = page == 1,
                        isNewSort = isNewSort,
                    )
                    recursive(page + 1)
                }
            }
        }

        return recursive(1)
    }

    fun emptyTrash(driveId: Int) = liveData(Dispatchers.IO) {
        emit(ApiRepository.emptyTrash(driveId))
    }

    fun cancelTrashFileJob() {
        getDeletedFilesJob?.cancel()
    }
}
