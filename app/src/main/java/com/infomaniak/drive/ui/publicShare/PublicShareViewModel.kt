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

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderArgs
import com.infomaniak.drive.data.cache.FolderFilesProvider.FolderFilesProviderResult
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.drive.utils.saveToKDrive
import com.infomaniak.drive.utils.shareFile
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.*

class PublicShareViewModel(val savedStateHandle: SavedStateHandle) : ViewModel() {

    var rootSharedFile = SingleLiveEvent<File?>()
    val childrenLiveData = SingleLiveEvent<Pair<List<File>, Boolean>>()
    var fileClicked: File? = null
    val downloadProgressLiveData = MutableLiveData(0)
    val buildArchiveResult = SingleLiveEvent<Pair<Int?, ArchiveUUID?>>()

    val driveId: Int
        inline get() = savedStateHandle[PublicShareActivityArgs::driveId.name] ?: ROOT_SHARED_FILE_ID

    val publicShareUuid: String
        inline get() = savedStateHandle[PublicShareActivityArgs::publicShareUuid.name] ?: ""

    val isPasswordNeeded: Boolean
        inline get() = savedStateHandle[PublicShareActivityArgs::isPasswordNeeded.name] ?: false

    private val fileId: Int
        inline get() = savedStateHandle[PublicShareActivityArgs::fileId.name] ?: ROOT_SHARED_FILE_ID

    private var getPublicShareFilesJob: Job = Job()
    private var currentCursor: String? = null

    fun downloadPublicShareRootFile() = viewModelScope.launch(Dispatchers.IO) {
        val file = if (fileId == ROOT_SHARED_FILE_ID) {
            rootSharedFile.value
        } else {
            val apiResponse = ApiRepository.getPublicShareRootFile(driveId, publicShareUuid, fileId)
            if (!apiResponse.isSuccess()) {
                SentryLog.w(TAG, "downloadSharedFile: ${apiResponse.error?.code}")
            }
            apiResponse.data
        }

        rootSharedFile.postValue(file?.apply { publicShareUuid = this@PublicShareViewModel.publicShareUuid })
    }

    fun getFiles(folderId: Int, sortType: SortType) {
        cancelDownload()
        getPublicShareFilesJob = Job()

        viewModelScope.launch(Dispatchers.IO + getPublicShareFilesJob) {

            tailrec fun recursiveDownload(folderId: Int, isFirstPage: Boolean) {

                val folderFilesProviderResult = loadFromRemote(
                    FolderFilesProviderArgs(folderId = folderId, isFirstPage = isFirstPage, order = sortType),
                )

                if (folderFilesProviderResult == null) return

                ensureActive()

                val newFiles = mutableListOf<File>().apply {
                    childrenLiveData.value?.first?.let(::addAll)
                    addAll(folderFilesProviderResult.folderFiles.addPublicShareUuid())
                }

                childrenLiveData.postValue(newFiles to true)
                if (!folderFilesProviderResult.isComplete) recursiveDownload(folderId, isFirstPage = false)
            }

            recursiveDownload(folderId, isFirstPage = true)
        }
    }

    fun cancelDownload() {
        getPublicShareFilesJob.cancel()
        getPublicShareFilesJob.cancelChildren()
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

    fun executeDownloadAction(
        activityContext: Context,
        downloadAction: DownloadAction,
        file: File?,
        navigateToDownloadDialog: suspend () -> Unit,
        onDownloadSuccess: () -> Unit,
        onDownloadError: () -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val cacheFile = file!!.convertToIOFile(
                context = activityContext,
                onProgress = downloadProgressLiveData::postValue,
                navigateToDownloadDialog = navigateToDownloadDialog,
            )

            val uri = FileProvider.getUriForFile(activityContext, activityContext.getString(R.string.FILE_AUTHORITY), cacheFile)

            when (downloadAction) {
                DownloadAction.OPEN_WITH -> {
                    activityContext.openWith(uri, file.getMimeType(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                DownloadAction.SEND_COPY -> activityContext.shareFile { uri }
                DownloadAction.SAVE_TO_DRIVE -> activityContext.saveToKDrive(uri)
                DownloadAction.OPEN_BOOKMARK -> TODO()
                DownloadAction.PRINT_PDF -> activityContext.printPdf(cacheFile)
            }

            withContext(Dispatchers.Main) { onDownloadSuccess() }
        }.onFailure { exception ->
            downloadProgressLiveData.postValue(null)
            exception.printStackTrace()
            withContext(Dispatchers.Main) { onDownloadError() }
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

        getPublicShareFilesJob.ensureActive()

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

    private fun List<File>.addPublicShareUuid() = map { it.apply { publicShareUuid = this@PublicShareViewModel.publicShareUuid } }

    companion object {
        const val TAG = "publicShareViewModel"
        const val ROOT_SHARED_FILE_ID = 1
    }
}
