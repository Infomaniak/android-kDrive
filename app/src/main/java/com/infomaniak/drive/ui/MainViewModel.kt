/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
package com.infomaniak.drive.ui

import android.content.Context
import android.net.Uri
import androidx.collection.arrayMapOf
import androidx.lifecycle.*
import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.SingleLiveEvent
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.*

class MainViewModel : ViewModel() {

    val currentFolder = MutableLiveData<File>()
    val currentFolderOpenAddFileBottom = MutableLiveData<File>()
    val currentFileList = MutableLiveData<ArrayList<File>>()
    val isInternetAvailable = MutableLiveData(true)

    val createDropBoxSuccess = SingleLiveEvent<DropBox>()

    val intentShowProgressByFolderId = SingleLiveEvent<Int>()

    val refreshActivities = SingleLiveEvent<Boolean>()
    val updateOfflineFile = SingleLiveEvent<Pair<Int, Boolean>>()
    val fileInProgress = SingleLiveEvent<FileInProgress>()
    val forcedDriveSelection = SingleLiveEvent<Boolean>()
    val deleteFileFromHome = SingleLiveEvent<Boolean>()

    val fileCancelledFromDownload = MutableLiveData<Int>()

    private var getFileDetailsJob = Job()
    private var syncOfflineFilesJob = Job()

    fun createMultiSelectMediator(): MediatorLiveData<Pair<Int, Int>> {
        return MediatorLiveData<Pair<Int, Int>>().apply { value = /*success*/0 to /*total*/0 }
    }

    fun updateMultiSelectMediator(mediator: MediatorLiveData<Pair<Int, Int>>): (ApiResponse<*>) -> Unit = { apiResponse ->
        val total = mediator.value!!.second + 1
        mediator.value =
            if (apiResponse.isSuccess()) mediator.value!!.first + 1 to total
            else mediator.value!!.first to total
    }

    fun postFileShareLink(
        file: File,
        body: Map<String, String> = mapOf("permission" to "public", "block_downloads" to "false", "can_edit" to "false")
    ) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.postFileShareLink(file, body)
        if (apiResponse.isSuccess()) {
            FileController.updateFile(file.id) { it.shareLink = apiResponse.data?.url }
        }
        emit(apiResponse)
    }


    fun getDropBox(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.getDropBox(file))
    }

    fun createDropBoxFolder(
        file: File,
        emailWhenFinished: Boolean,
        limitFileSize: Long? = null,
        password: String? = null,
        validUntil: Long? = null
    ) = liveData(Dispatchers.IO) {
        val body = arrayMapOf(
            "email_when_finished" to emailWhenFinished,
            "limit_file_size" to limitFileSize,
            "password" to password
        )
        validUntil?.let { body.put("valid_until", validUntil) }
        emit(ApiRepository.postDropBox(file, body))
    }

    fun updateDropBox(file: File, newDropBox: DropBox) = liveData(Dispatchers.IO) {
        val data = JsonObject().apply {
            addProperty("email_when_finished", newDropBox.newEmailWhenFinished)
            addProperty("valid_until", newDropBox.newValidUntil?.time?.let { it / 1000 })
            addProperty("limit_file_size", newDropBox.newLimitFileSize)
            addProperty("with_limit_file_size", newDropBox.withLimitFileSize)

            if (newDropBox.newPassword && !newDropBox.newPasswordValue.isNullOrBlank()) {
                addProperty("password", newDropBox.newPasswordValue)
            } else if (!newDropBox.newPassword) {
                val password: String? = null
                addProperty("password", password)
            }
        }
        val apiResponse = ApiRepository.updateDropBox(file, data)
        emit(apiResponse)
    }

    fun deleteDropBox(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteDropBox(file))
    }

    fun deleteFileShareLink(file: File) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.deleteFileShareLink(file)
        if (apiResponse.isSuccess()) FileController.updateFile(file.id) {
            it.shareLink = null
            it.rights?.canBecomeLink = true
        }
        emit(apiResponse)
    }

    fun getFileShare(fileId: Int, userDrive: UserDrive? = null) = liveData(Dispatchers.IO) {
        val okHttpClient = userDrive?.userId?.let { KDriveHttpClient.getHttpClient(it) } ?: HttpClient.okHttpClient
        val driveId = userDrive?.driveId ?: AccountUtils.currentDriveId
        val apiResponse = ApiRepository.getFileShare(okHttpClient, File(id = fileId, driveId = driveId))
        emit(apiResponse)
    }

    fun createOffice(driveId: Int, folderId: Int, createFile: CreateFile) = liveData(Dispatchers.IO) {
        emit(ApiRepository.createOfficeFile(driveId, folderId, createFile))
    }

    fun addFileToFavorites(file: File, onSuccess: (() -> Unit)? = null) = liveData(Dispatchers.IO) {
        ApiRepository.postFavoriteFile(file).let { apiResponse ->
            if (apiResponse.isSuccess()) {
                FileController.updateFile(file.id) { localFile ->
                    localFile.isFavorite = true
                }
                onSuccess?.invoke()
            }
            emit(apiResponse)
        }
    }

    fun deleteFileFromFavorites(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteFavoriteFile(file))
    }

    fun getFileDetails(fileId: Int, userDrive: UserDrive): LiveData<File?> {
        getFileDetailsJob.cancel()
        getFileDetailsJob = Job()
        return liveData(Dispatchers.IO + getFileDetailsJob) {
            emit(FileController.getFileDetails(fileId, userDrive))
        }
    }

    fun moveFile(
        file: File,
        newParent: File,
        onSuccess: ((fileID: Int) -> Unit)? = null
    ) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.moveFile(file, newParent)
        if (apiResponse.isSuccess()) {
            FileController.removeFile(file.id, recursive = false)

            FileController.updateFile(newParent.id) { localFolder ->
                file.isOffline = false
                localFolder.children.add(file)
            }

            onSuccess?.invoke(file.id)
        }
        emit(apiResponse)
    }

    fun renameFile(file: File, newName: String) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.renameFile(file, newName)
        if (apiResponse.isSuccess()) {
            FileController.updateFile(file.id) { localFile ->
                localFile.name = newName
            }
        }
        emit(apiResponse)
    }

    fun deleteFile(context: Context, file: File, onSuccess: ((fileID: Int) -> Unit)? = null) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.deleteFile(file)
        if (apiResponse.isSuccess()) {
            file.deleteCaches(context)

            FileController.updateFile(file.id) { localFile ->
                localFile.deleteFromRealm()
            }
            onSuccess?.invoke(file.id)
        }
        emit(apiResponse)
    }

    fun duplicateFile(file: File, folderId: Int? = null, copyName: String?) = liveData(Dispatchers.IO) {
        emit(ApiRepository.duplicateFile(file, copyName, folderId ?: Utils.ROOT_ID))
    }

    fun convertFile(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.convertFile(file))
    }

    suspend fun removeOfflineFile(
        context: Context,
        file: File,
        offlineFile: java.io.File,
        cacheFile: java.io.File,
        userDrive: UserDrive = UserDrive()
    ) = withContext(Dispatchers.IO) {
        FileController.updateOfflineStatus(file.id, false)
        if (file.isMedia()) file.deleteInMediaScan(context, userDrive)
        if (cacheFile.exists()) cacheFile.delete()
        if (offlineFile.exists()) {
            offlineFile.copyTo(cacheFile)
            offlineFile.delete()
        }
    }

    private fun migrateOfflineIfNeeded(context: Context, file: File, offlineFile: java.io.File, userDrive: UserDrive) {
        val oldPath = java.io.File(context.filesDir, "offline_storage/${userDrive.userId}/${userDrive.driveId}/${file.id}")
        if (oldPath.exists()) oldPath.renameTo(offlineFile)
    }

    suspend fun syncOfflineFiles(context: Context) {
        syncOfflineFilesJob.cancel()
        syncOfflineFilesJob = Job()
        runInterruptible(Dispatchers.IO + syncOfflineFilesJob) {
            DriveInfosController.getDrives(AccountUtils.currentUserId).forEach { drive ->
                val userDrive = UserDrive(driveId = drive.id)

                FileController.getOfflineFiles(null, userDrive).forEach { file ->
                    val offlineFile = file.getOfflineFile(context, userDrive)
                    val localLastModified = offlineFile.lastModified() / 1000

                    migrateOfflineIfNeeded(context, file, offlineFile, userDrive)

                    val apiResponse = ApiRepository.getFileDetails(file)
                    apiResponse.data?.let { remoteFile ->
                        if (localLastModified > file.lastModifiedAt) uploadFile(context, file, remoteFile, offlineFile, userDrive)
                        else downloadFile(context, file, remoteFile, offlineFile, userDrive)
                    } ?: let {
                        if (apiResponse.error?.code?.equals("object_not_found") == true) offlineFile.delete()
                    }
                }
            }
        }
    }

    private fun uploadFile(context: Context, file: File, remoteFile: File, offlineFile: java.io.File, userDrive: UserDrive) {
        val uri = Uri.fromFile(offlineFile)
        val fileModifiedAt = Date(offlineFile.lastModified())
        if (UploadFile.canUpload(uri, fileModifiedAt)) {
            remoteFile.lastModifiedAt = offlineFile.lastModified() / 1000
            remoteFile.size = offlineFile.length()
            FileController.updateExistingFile(newFile = remoteFile, userDrive = userDrive)
            UploadFile(
                uri = uri.toString(),
                driveId = userDrive.driveId,
                fileModifiedAt = fileModifiedAt,
                fileName = file.name,
                fileSize = offlineFile.length(),
                remoteFolder = FileController.getParentFile(file.id, userDrive)!!.id,
                type = UploadFile.Type.SYNC_OFFLINE.name,
                userId = userDrive.userId,
            ).store()
            context.syncImmediately()
        }
    }

    private fun downloadFile(context: Context, file: File, remoteFile: File, offlineFile: java.io.File, userDrive: UserDrive) {
        file.lastModifiedAt = remoteFile.lastModifiedAt
        val remoteOfflineFile = remoteFile.getOfflineFile(context, userDrive)
        val pathChanged = !offlineFile.path.equals(remoteOfflineFile.path)
        if (pathChanged) {
            if (file.isMedia()) file.deleteInMediaScan(context, userDrive)
            offlineFile.delete()
        }

        if (!file.isPendingOffline(context) && (!remoteFile.isOfflineAndIntact(offlineFile) || pathChanged)) {
            FileController.updateExistingFile(newFile = remoteFile, userDrive = userDrive)
            Utils.downloadAsOfflineFile(context, remoteFile, userDrive)
        }
    }
}
