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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class TrashViewModel : ViewModel() {

    val selectedFile = MutableLiveData<File>()
    val removeFileId = SingleLiveEvent<Int>()
    private var getDeletedFilesJob: Job = Job()

    fun getTrashFile(file: File, order: File.SortType): LiveData<FileListFragment.FolderFilesResult?> {
        getDeletedFilesJob.cancel()
        getDeletedFilesJob = Job()
        return liveData(Dispatchers.IO + getDeletedFilesJob) {
            suspend fun recursive(page: Int) {
                val apiResponse = ApiRepository.getTrashFile(file, order, page)
                if (apiResponse.isSuccess()) {
                    val data = apiResponse.data
                    when {
                        data == null -> Unit
                        data.children.size < ApiRepository.PER_PAGE -> emit(
                            FileListFragment.FolderFilesResult(
                                parentFolder = file,
                                files = ArrayList(data.children),
                                isComplete = true,
                                page = page
                            )
                        )
                        else -> {
                            emit(
                                FileListFragment.FolderFilesResult(
                                    parentFolder = file,
                                    files = ArrayList(data.children),
                                    isComplete = false,
                                    page = page
                                )
                            )
                            recursive(page + 1)
                        }
                    }
                } else emit(null)
            }
            recursive(1)
        }
    }

    fun getDriveTrash(driveId: Int, order: File.SortType): LiveData<FileListFragment.FolderFilesResult?> {
        getDeletedFilesJob.cancel()
        getDeletedFilesJob = Job()
        return liveData(Dispatchers.IO + getDeletedFilesJob) {
            suspend fun recursive(page: Int) {
                val apiResponse = ApiRepository.getDriveTrash(driveId, order, page)
                if (apiResponse.isSuccess()) {
                    when {
                        apiResponse.data.isNullOrEmpty() -> emit(null)
                        apiResponse.data!!.size < ApiRepository.PER_PAGE -> emit(
                            FileListFragment.FolderFilesResult(
                                files = apiResponse.data!!,
                                isComplete = true,
                                page = page
                            )
                        )
                        else -> {
                            emit(
                                FileListFragment.FolderFilesResult(
                                    files = apiResponse.data!!,
                                    isComplete = false,
                                    page = page
                                )
                            )
                            recursive(page + 1)
                        }
                    }
                } else emit(null)
            }
            recursive(1)
        }
    }

    fun deleteTrashFile(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteTrashFile(file))
    }

    fun restoreTrashFile(file: File, newFolder: File? = null) =
        liveData(Dispatchers.IO) {
            val body = newFolder?.let { mapOf("destination_folder_id" to newFolder.id) }
            emit(ApiRepository.postRestoreTrashFile(file, body))
        }

    fun emptyTrash(driveId: Int) = liveData(Dispatchers.IO) {
        emit(ApiRepository.emptyTrash(driveId))
    }

    fun cancelTrashFileJob() {
        getDeletedFilesJob.cancel()
    }

}