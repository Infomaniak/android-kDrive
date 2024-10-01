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
package com.infomaniak.drive.ui.publicShare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderArgs
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderResult
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class PublicShareViewModel(application: Application, val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val appContext = getApplication<MainApplication>()

    var rootSharedFile = SingleLiveEvent<File?>()
    val childrenLiveData = SingleLiveEvent<PublicShareFilesResult>()
    var fileClicked: File? = null
    val downloadProgressLiveData = MutableLiveData(0)
    val buildArchiveResult = SingleLiveEvent<Pair<Int?, ArchiveUUID?>>()
    val fetchCacheFileForActionResult = MutableSharedFlow<Pair<IOFile?, DownloadAction>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val initPublicShareResult = SingleLiveEvent<Pair<ApiError?, ShareLink?>>()
    val submitPasswordResult = SingleLiveEvent<Boolean?>()
    var hasBeenAuthenticated = false
    var canDownloadFiles = canDownload

    val driveId: Int
        inline get() = savedStateHandle[PublicShareActivityArgs::driveId.name] ?: ROOT_SHARED_FILE_ID

    val publicShareUuid: String
        inline get() = savedStateHandle[PublicShareActivityArgs::publicShareUuid.name] ?: ""

    val isPasswordNeeded: Boolean
        inline get() = savedStateHandle[PublicShareActivityArgs::isPasswordNeeded.name] ?: false

    val isExpired: Boolean
        inline get() = savedStateHandle[PublicShareActivityArgs::isExpired.name] ?: false

    private val canDownload: Boolean
        inline get() = savedStateHandle[PublicShareActivityArgs::canDownload.name] ?: false

    private val fileId: Int
        inline get() = savedStateHandle[PublicShareActivityArgs::fileId.name] ?: ROOT_SHARED_FILE_ID

    private var getPublicShareFilesJob: Job = Job()
    private var currentCursor: String? = null

    override fun onCleared() {
        cancelDownload()
        super.onCleared()
    }

    fun initPublicShare() {
        val apiResponse = ApiRepository.getPublicShareInfo(driveId, publicShareUuid)
        val result = if (apiResponse.isSuccess()) null to apiResponse.data else apiResponse.error to null

        initPublicShareResult.postValue(result)
    }

    fun submitPublicSharePassword(password: String) = viewModelScope.launch(Dispatchers.IO) {
        submitPasswordResult.postValue(ApiRepository.submitPublicSharePassword(driveId, publicShareUuid, password).data)
    }

    fun downloadPublicShareRootFile() = viewModelScope.launch(Dispatchers.IO) {
        val file = if (fileId == ROOT_SHARED_FILE_ID) {
            rootSharedFile.value
        } else {
            val apiResponse = ApiRepository.getPublicShareRootFile(driveId, publicShareUuid, fileId)
            if (!apiResponse.isSuccess()) SentryLog.w(TAG, "downloadSharedFile: ${apiResponse.error?.code}")
            apiResponse.data
        }

        rootSharedFile.postValue(file?.apply { publicShareUuid = this@PublicShareViewModel.publicShareUuid })
    }

    fun getFiles(folderId: Int, sortType: SortType, isNewSort: Boolean) {
        getPublicShareFilesJob = Job()

        viewModelScope.launch(Dispatchers.IO + getPublicShareFilesJob) {

            tailrec fun recursiveDownload(folderId: Int, isFirstPage: Boolean) {

                val folderFilesProviderResult = loadFromRemote(
                    FolderFilesProviderArgs(folderId = folderId, isFirstPage = isFirstPage, order = sortType),
                )

                if (folderFilesProviderResult == null) return

                ensureActive()

                val newFiles = mutableListOf<File>().apply {
                    childrenLiveData.value?.files?.let(::addAll)
                    addAll(folderFilesProviderResult.folderFiles.addPublicShareUuid())
                    if (any(File::isFolder)) sortByDescending(File::isFolder)
                }

                childrenLiveData.postValue(PublicShareFilesResult(files = newFiles, shouldUpdate = true, isNewSort = isNewSort))
                currentCursor = folderFilesProviderResult.cursor
                if (!folderFilesProviderResult.isComplete) recursiveDownload(folderId, isFirstPage = false)
            }

            recursiveDownload(folderId, isFirstPage = true)
        }
    }

    fun cancelDownload() {
        getPublicShareFilesJob.cancel()
        getPublicShareFilesJob.cancelChildren()
        currentCursor = null
    }

    fun importFilesToDrive(
        destinationDriveId: Int,
        destinationFolderId: Int,
        fileIds: List<Int>,
        exceptedFileIds: List<Int>,
    ) = viewModelScope.launch(Dispatchers.IO) {
        ApiRepository.importPublicShareFiles(
            sourceDriveId = driveId,
            linkUuid = publicShareUuid,
            destinationDriveId = destinationDriveId,
            destinationFolderId = destinationFolderId,
            fileIds = fileIds,
            exceptedFileIds = exceptedFileIds,
        )

        // TODO: Manage apiResponse when the backend will be done
    }

    fun buildArchive(archiveBody: ArchiveUUID.ArchiveBody) = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.buildPublicShareArchive(driveId, publicShareUuid, archiveBody)
        val result = apiResponse.data?.let { archiveUuid -> null to archiveUuid } ?: (apiResponse.translatedError to null)

        buildArchiveResult.postValue(result)
    }

    fun setSingleRootFile(file: File?) {
        val fileList = file?.let(::listOf) ?: listOf()
        childrenLiveData.postValue(
            PublicShareFilesResult(fileList, shouldUpdate = true, isNewSort = false)
        )
    }

    fun fetchCacheFileForAction(file: File?, action: DownloadAction, navigateToDownloadDialog: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fetchCacheFileForActionResult.emit(
                    file!!.convertToIOFile(
                        context = appContext,
                        onProgress = downloadProgressLiveData::postValue,
                        navigateToDownloadDialog = navigateToDownloadDialog,
                    ) to action
                )
            }.onFailure { exception ->
                fetchCacheFileForActionResult.emit(null to action)
                downloadProgressLiveData.postValue(null)
                exception.printStackTrace()
            }
        }
    }

    private fun loadFromRemote(folderFilesProviderArgs: FolderFilesProviderArgs): FolderFilesProviderResult? {
        val apiResponse = ApiRepository.getPublicShareChildrenFiles(
            driveId = driveId,
            linkUuid = publicShareUuid,
            folderId = folderFilesProviderArgs.folderId,
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

        return handleRemoteFiles(apiResponse)
    }

    private fun handleRemoteFiles(apiResponse: CursorApiResponse<List<File>>) = apiResponse.data?.let { remoteFiles ->
        //TODO: Better management of this rootSharedFile value
        FolderFilesProviderResult(
            folder = rootSharedFile.value!!,
            folderFiles = ArrayList(remoteFiles),
            isComplete = !apiResponse.hasMore,
            cursor = apiResponse.cursor,
        )
    }

    private fun List<File>.addPublicShareUuid() = map { it.apply { publicShareUuid = this@PublicShareViewModel.publicShareUuid } }

    data class PublicShareFilesResult(
        val files: List<File>,
        val shouldUpdate: Boolean,
        val isNewSort: Boolean,
    )

    companion object {
        const val TAG = "publicShareViewModel"
        const val ROOT_SHARED_FILE_ID = 1
    }
}
