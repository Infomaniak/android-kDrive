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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderArgs
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderResult
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.*

class FileSharedViewModel(val savedStateHandle: SavedStateHandle) : ViewModel() {

    var rootSharedFile = SingleLiveEvent<File?>()
    val childrenLiveData = SingleLiveEvent<Pair<List<File>, Boolean>>()

    private val driveId: Int
        inline get() = savedStateHandle[FileSharedActivityArgs::driveId.name] ?: ROOT_SHARED_FILE_ID

    val fileSharedLinkUuid: String
        inline get() = savedStateHandle[FileSharedActivityArgs::fileSharedLinkUuid.name] ?: ""

    private val fileId: Int
        inline get() = savedStateHandle[FileSharedActivityArgs::fileId.name] ?: ROOT_SHARED_FILE_ID

    private var getSharedFilesJob: Job = Job()
    private var currentCursor: String? = null

    fun downloadSharedFile() = viewModelScope.launch(Dispatchers.IO) {
        val file = if (fileId == ROOT_SHARED_FILE_ID) {
            rootSharedFile.value
        } else {
            val apiResponse = ApiRepository.getShareLinkFile(driveId, fileSharedLinkUuid, fileId)
            if (!apiResponse.isSuccess()) {
                SentryLog.w(TAG, "downloadSharedFile: ${apiResponse.error?.code}")
            }
            apiResponse.data
        }

        rootSharedFile.postValue(file)
    }

    fun getFiles(folderId: Int, sortType: SortType) {
        cancelDownload()
        getSharedFilesJob = Job()

        viewModelScope.launch(Dispatchers.IO + getSharedFilesJob) {

            tailrec fun recursiveDownload(folderId: Int, isFirstPage: Boolean) {

                val folderFilesProviderResult = loadFromRemote(
                    FolderFilesProviderArgs(folderId = folderId, isFirstPage = isFirstPage, order = sortType),
                )

                if (folderFilesProviderResult == null) return

                ensureActive()

                val newFiles = mutableListOf<File>().apply {
                    childrenLiveData.value?.first?.let(::addAll)
                    addAll(folderFilesProviderResult.folderFiles.addShareLinkUuid())
                }

                childrenLiveData.postValue(newFiles to true)
                if (!folderFilesProviderResult.isComplete) recursiveDownload(folderId, isFirstPage = false)
            }

            recursiveDownload(folderId, isFirstPage = true)
        }
    }

    fun cancelDownload() {
        getSharedFilesJob.cancel()
        getSharedFilesJob.cancelChildren()
    }

    private fun loadFromRemote(folderFilesProviderArgs: FolderFilesProviderArgs): FolderFilesProviderResult? {
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

        getSharedFilesJob.ensureActive()

        return handleRemoteFiles(apiResponse)
    }

    private fun handleRemoteFiles(apiResponse: CursorApiResponse<List<File>>) = apiResponse.data?.let { remoteFiles ->
        currentCursor = apiResponse.cursor
        //TODO: Better management of this rootSharedFile value
        FolderFilesProviderResult(
            folder = rootSharedFile.value!!,
            folderFiles = ArrayList(remoteFiles),
            isComplete = !apiResponse.hasMore,
        )
    }

    private fun List<File>.addShareLinkUuid() = map { it.apply { externalShareLinkUuid = fileSharedLinkUuid } }

    companion object {
        const val TAG = "FileSharedViewModel"
        const val ROOT_SHARED_FILE_ID = 1
    }
}