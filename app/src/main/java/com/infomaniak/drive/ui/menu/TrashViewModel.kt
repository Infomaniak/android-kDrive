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
import kotlinx.coroutines.ensureActive
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
            getDriveTrash(driveId, order, isNewSort)
        }
    }

    fun loadTrashedFolderFiles(file: File, order: File.SortType, isNewSort: Boolean) {
        getDeletedFilesJob?.cancel()
        getDeletedFilesJob = viewModelScope.launch(Dispatchers.IO) {
            getTrashedFolderFiles(file, order, isNewSort)
        }
    }

    private fun getTrashedFolderFiles(file: File, order: File.SortType, isNewSort: Boolean) {

        tailrec fun recursive(isFirstPage: Boolean, isNewSort: Boolean, cursor: String? = null) {
            val apiResponse = ApiRepository.getTrashedFolderFiles(file, order, cursor)
            val apiResponseData = apiResponse.data
            getDeletedFilesJob?.ensureActive()

            when {
                apiResponseData.isNullOrEmpty() -> trashedFolderFilesResults.postValue(null)
                apiResponse.hasMoreAndCursorExists -> {
                    trashedFolderFilesResults.postValue(
                        FolderFilesResult(
                            parentFolder = file,
                            files = apiResponseData,
                            isComplete = false,
                            isFirstPage = isFirstPage,
                            isNewSort = isNewSort,
                        )
                    )
                    getDeletedFilesJob?.ensureActive()
                    recursive(isFirstPage = false, isNewSort = false, cursor = apiResponse.cursor)
                }
                else -> {
                    trashedFolderFilesResults.postValue(
                        FolderFilesResult(
                            parentFolder = file,
                            files = apiResponseData,
                            isComplete = true,
                            isFirstPage = isFirstPage,
                            isNewSort = isNewSort,
                        )
                    )
                }
            }
        }

        recursive(isFirstPage = true, isNewSort = isNewSort)
    }

    private fun getDriveTrash(driveId: Int, order: File.SortType, isNewSort: Boolean) {

        tailrec fun recursive(isFirstPage: Boolean, isNewSort: Boolean, cursor: String? = null) {
            val apiResponse = ApiRepository.getDriveTrash(driveId, order, cursor)
            val apiResponseData = apiResponse.data
            getDeletedFilesJob?.ensureActive()
            when {
                apiResponseData.isNullOrEmpty() -> driveTrashResults.postValue(null)
                apiResponse.hasMoreAndCursorExists -> {
                    driveTrashResults.postValue(
                        FolderFilesResult(
                            files = apiResponseData,
                            isComplete = false,
                            isFirstPage = isFirstPage,
                            isNewSort = isNewSort,
                        )
                    )
                    getDeletedFilesJob?.ensureActive()
                    recursive(isFirstPage = false, isNewSort = false, cursor = apiResponse.cursor)
                }
                else -> {
                    driveTrashResults.postValue(
                        FolderFilesResult(
                            files = apiResponseData,
                            isComplete = true,
                            isFirstPage = isFirstPage,
                            isNewSort = isNewSort,
                        )
                    )
                }
            }
        }

        recursive(isFirstPage = true, isNewSort = isNewSort)
    }

    fun emptyTrash(driveId: Int) = liveData(Dispatchers.IO) {
        emit(ApiRepository.emptyTrash(driveId))
    }

    fun cancelTrashFileJob() {
        getDeletedFilesJob?.cancel()
    }
}
