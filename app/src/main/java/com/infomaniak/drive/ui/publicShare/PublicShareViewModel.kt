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
import com.infomaniak.drive.BuildConfig
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
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class PublicShareViewModel(application: Application, val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val appContext = getApplication<MainApplication>()

    var rootSharedFile = SingleLiveEvent<File?>()
    val childrenLiveData = SingleLiveEvent<PublicShareFilesResult>()
    var fileClicked: File? = null
    val downloadProgressLiveData = MutableLiveData(0)
    val buildArchiveResult = SingleLiveEvent<Pair<Int?, ArchiveUUID?>>()
    val initPublicShareResult = SingleLiveEvent<Pair<ApiError?, ShareLink?>>()
    val importPublicShareResult = SingleLiveEvent<Pair<Int?, String>>()
    val submitPasswordResult = SingleLiveEvent<Boolean?>()
    var hasBeenAuthenticated = false
    var canDownloadFiles = canDownload

    private val _fetchCacheFileForActionResult = MutableSharedFlow<Pair<IOFile?, DownloadAction>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val fetchCacheFileForActionResult: SharedFlow<Pair<IOFile?, DownloadAction>> = _fetchCacheFileForActionResult

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
        val apiResponse = ApiRepository.importPublicShareFiles(
            sourceDriveId = driveId,
            linkUuid = publicShareUuid,
            destinationDriveId = destinationDriveId,
            destinationFolderId = destinationFolderId,
            fileIds = fileIds,
            exceptedFileIds = exceptedFileIds,
        )

        val error = if (apiResponse.isSuccess()) null else apiResponse.translateError()
        val destinationPath = "${BuildConfig.SHARE_URL_V1}drive/$destinationDriveId/files/$destinationFolderId"
        importPublicShareResult.postValue(error to destinationPath)
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
                _fetchCacheFileForActionResult.emit(
                    file!!.convertToIOFile(
                        context = appContext,
                        onProgress = downloadProgressLiveData::postValue,
                        navigateToDownloadDialog = navigateToDownloadDialog,
                    ) to action
                )
            }.onFailure { exception ->
                _fetchCacheFileForActionResult.emit(null to action)
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
        rootSharedFile.value?.let { parentFolder ->
            FolderFilesProviderResult(
                folder = parentFolder,
                folderFiles = ArrayList(remoteFiles),
                isComplete = !apiResponse.hasMore,
                cursor = apiResponse.cursor,
            )
        } ?: run {
            Sentry.captureMessage("Root folder is null in HandleRemoteFiles, that should not happen") { scope ->
                scope.setExtra("publicShareUuid", publicShareUuid)
                scope.setTag("isPasswordNeeded", isPasswordNeeded.toString())
                scope.setTag("isExpired", isExpired.toString())
                scope.setTag("canDownload", canDownload.toString())
            }
            null
        }
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
