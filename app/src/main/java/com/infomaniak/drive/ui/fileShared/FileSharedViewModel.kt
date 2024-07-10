/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileShared

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderArgs
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderResult
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class FileSharedViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    var rootSharedFile: File? = null
    val childrenLiveData = SingleLiveEvent<Pair<List<File>, Boolean>>()

    private val driveId: Int
        inline get() = savedStateHandle[FileSharedActivityArgs::driveId.name] ?: ROOT_SHARED_FILE_ID

    private val fileSharedLinkUuid: String
        inline get() = savedStateHandle[FileSharedActivityArgs::fileSharedLinkUuid.name] ?: ""

    private val fileId: Int
        inline get() = savedStateHandle[FileSharedActivityArgs::fileId.name] ?: ROOT_SHARED_FILE_ID

    private var getSharedFilesJob: Job = Job()
    private var currentCursor: String? = null

    fun downloadSharedFile() = viewModelScope.launch(Dispatchers.IO) {
        val file = if (fileId == ROOT_SHARED_FILE_ID) {
            rootSharedFile
        } else {
            val apiResponse = ApiRepository.getShareLinkFile(driveId, fileSharedLinkUuid, fileId)
            if (apiResponse.isSuccess() && apiResponse.data != null) {
                val sharedFile = apiResponse.data!!
                rootSharedFile = sharedFile
                sharedFile
            } else {
                Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
                null
            }
        }

        childrenLiveData.postValue((file?.let(::listOf) ?: listOf()) to true)
    }

    fun downloadSharedFileChildren(folderId: Int, sortType: SortType) = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.getShareLinkFileChildren(driveId, fileSharedLinkUuid, folderId, sortType, currentCursor)
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            childrenLiveData.postValue(apiResponse.data!! to true)
        } else {
            Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
            childrenLiveData.postValue(emptyList<File>() to true)
        }
    }

    fun getFiles(folderId: Int, sortType: SortType, isNewSort: Boolean) {
        getSharedFilesJob.cancel()
        getSharedFilesJob = Job()

        viewModelScope.launch(Dispatchers.IO + getSharedFilesJob) {

            tailrec suspend fun recursiveDownload(folderId: Int, isFirstPage: Boolean) {
                Log.e("TOTO", "recursiveDownload: isFirstPage = $isFirstPage / currentCursor = $currentCursor")
                getSharedFilesJob.ensureActive()

                val folderFilesProviderResult = loadFromRemote(
                    FolderFilesProviderArgs(folderId = folderId, isFirstPage = isFirstPage, order = sortType),
                )

                when {
                    folderFilesProviderResult == null -> Unit
                    folderFilesProviderResult.isComplete -> {
                        Log.e("TOTO", "recursiveDownload: isComplete")
                        childrenLiveData.postValue(
                            ((childrenLiveData.value?.first ?: emptyList()) + folderFilesProviderResult.folderFiles) to true
                        )
//                        emit(
//                            FolderFilesResult(
//                                parentFolder = folderFilesProviderResult.folder,
//                                files = folderFilesProviderResult.folderFiles,
//                                isComplete = true,
//                                isFirstPage = isFirstPage,
//                                isNewSort = isNewSort,
//                            )
//                        )
                    }
                    else -> {
                        if (isFirstPage) {
                            Log.e("TOTO", "recursiveDownload: isFirstPage")
                            childrenLiveData.postValue(
                                ((childrenLiveData.value?.first ?: emptyList()) + folderFilesProviderResult.folderFiles) to true
                            )
//                            emit(
//                                FolderFilesResult(
//                                    parentFolder = folderFilesProviderResult.folder,
//                                    files = folderFilesProviderResult.folderFiles,
//                                    isComplete = true,
//                                    isFirstPage = true,
//                                    isNewSort = false,
//                                )
//                            )
                        }
                        recursiveDownload(folderId, isFirstPage = false)
                    }
                }
            }

            recursiveDownload(folderId, isFirstPage = true)
        }
    }

    private fun loadFromRemote(
        folderFilesProviderArgs: FolderFilesProviderArgs,
    ): FolderFilesProviderResult? = with(Dispatchers.IO) {
        Log.e("TOTO", "loadFromRemote: ${folderFilesProviderArgs.folderId} / fileId = $fileId")
        val apiResponse = ApiRepository.getShareLinkFileChildren(
            driveId = driveId,
            linkUuid = fileSharedLinkUuid,
            fileId = folderFilesProviderArgs.folderId,
            sortType = folderFilesProviderArgs.order,
            cursor = currentCursor,
        ).let {
            CursorApiResponse(
                result = it.result,
                data = it.data,
                error = it.error,
                responseAt = it.responseAt,
                cursor = it.cursor,
                hasMore = it.hasMore,
            )
        }

        ensureActive()

        handleRemoteFiles(apiResponse)
    }


    private fun handleRemoteFiles(apiResponse: CursorApiResponse<List<File>>): FolderFilesProviderResult? {
        val apiResponseData = apiResponse.data

        return when {
            apiResponseData != null -> {
                currentCursor = apiResponse.cursor
                //TODO: Better management of this rootSharedFile value
                FolderFilesProviderResult(
                    folder = rootSharedFile!!,
                    folderFiles = ArrayList(apiResponseData),
                    isComplete = !apiResponse.hasMore
                )
            }
            else -> null
        }
    }

    companion object {
        const val ROOT_SHARED_FILE_ID = 1
    }
}