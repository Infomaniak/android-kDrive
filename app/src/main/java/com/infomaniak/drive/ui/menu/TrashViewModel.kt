/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class TrashViewModel : ViewModel() {

    val selectedFile = MutableLiveData<File>()
    val removeFileId = SingleLiveEvent<Int>()
    var trashResults = MutableLiveData<FolderFilesResult>()
    private var getDeletedFilesJob: Job? = null


    fun loadDriveTrash(driveId: Int, order: File.SortType, isNewSort: Boolean) {
        startLoadTrashContent(isNewSort = isNewSort) {
            ApiRepository.getDriveTrash(driveId, order, cursor = it)
        }
    }

    fun loadTrashedFolderFiles(file: File, order: File.SortType, isNewSort: Boolean) {
        startLoadTrashContent(parentFolder = file, isNewSort = isNewSort) {
            ApiRepository.getTrashedFolderFiles(file, order, cursor = it)
        }
    }

    private fun startLoadTrashContent(
        parentFolder: File? = null,
        isNewSort: Boolean,
        retrieveMethod: suspend (cursor: String?) -> CursorApiResponse<List<File>>,
    ) {
        getDeletedFilesJob?.cancel()
        getDeletedFilesJob = viewModelScope.launch(Dispatchers.IO) {
            retrieveTrashContent(
                isFirstPage = true,
                isNewSort = isNewSort,
                parentFolder = parentFolder,
                retrieveMethod = retrieveMethod
            )
        }
    }

    tailrec suspend fun retrieveTrashContent(
        isFirstPage: Boolean,
        isNewSort: Boolean,
        cursor: String? = null,
        parentFolder: File?,
        retrieveMethod: suspend (cursor: String?) -> CursorApiResponse<List<File>>,
    ) {
        val apiResponse = retrieveMethod(cursor)
        val hasMoreElements = apiResponse.hasMoreAndCursorExists
        getDeletedFilesJob?.ensureActive()
        trashResults.postValue(
            FolderFilesResult(
                parentFolder = parentFolder,
                files = apiResponse.data ?: listOf(),
                isComplete = !hasMoreElements,
                isFirstPage = isFirstPage,
                isNewSort = isNewSort,
            )
        )
        if (hasMoreElements) {
            getDeletedFilesJob?.ensureActive()
            retrieveTrashContent(
                isFirstPage = false,
                isNewSort = false,
                cursor = apiResponse.cursor,
                parentFolder = parentFolder,
                retrieveMethod = retrieveMethod
            )
        }
    }

    fun emptyTrash(driveId: Int) = liveData(Dispatchers.IO) {
        emit(ApiRepository.emptyTrash(driveId))
    }

    fun cancelTrashFileJob() {
        getDeletedFilesJob?.cancel()
    }
}
