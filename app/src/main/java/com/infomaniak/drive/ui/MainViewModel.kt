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
package com.infomaniak.drive.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import androidx.collection.arrayMapOf
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.gson.JsonObject
import com.infomaniak.drive.ApplicationMain
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import io.realm.Realm
import io.sentry.Sentry
import kotlinx.coroutines.*

class MainViewModel(appContext: Application) : AndroidViewModel(appContext) {

    var selectFolderUserDrive: UserDrive? = null
    val realm: Realm by lazy {
        selectFolderUserDrive?.let {
            FileController.getRealmInstance(it)
        } ?: FileController.getRealmInstance()
    }

    val currentFolder = MutableLiveData<File>()
    val currentFolderOpenAddFileBottom = MutableLiveData<File>()
    var currentPreviewFileList = LinkedHashMap<Int, File>()
    val isInternetAvailable = MutableLiveData(true)

    val createDropBoxSuccess = SingleLiveEvent<DropBox>()

    val navigateFileListToFolderId = SingleLiveEvent<FileId>()

    val deleteFileFromHome = SingleLiveEvent<Boolean>()
    val refreshActivities = SingleLiveEvent<Boolean>()
    val updateOfflineFile = SingleLiveEvent<FileId>()
    val updateVisibleFiles = MutableLiveData<Boolean>()

    private var getFileDetailsJob = Job()
    private var syncOfflineFilesJob = Job()

    private fun getContext() = getApplication<ApplicationMain>()

    fun navigateFileListToFolderId(navController: NavController, folderId: Int) {
        // Clear FileListFragment stack
        with(navController) {
            popBackStack(R.id.homeFragment, false)
            navigate(R.id.fileListFragment)
        }
        // Emit destination folder id
        if (folderId > Utils.ROOT_ID) navigateFileListToFolderId.value = folderId
    }

    fun createMultiSelectMediator(): MediatorLiveData<Pair<Int, Int>> {
        return MediatorLiveData<Pair<Int, Int>>().apply { value = /*success*/0 to /*total*/0 }
    }

    fun updateMultiSelectMediator(mediator: MediatorLiveData<Pair<Int, Int>>): (ApiResponse<*>) -> Unit = { apiResponse ->
        val total = mediator.value!!.second + 1
        mediator.value = if (apiResponse.isSuccess()) {
            mediator.value!!.first + 1 to total
        } else {
            mediator.value!!.first to total
        }
    }

    fun postFileShareLink(
        file: File,
        // TODO Changes for api-v2 : boolean instead of "true", "false", and can_edit instead of canEdit
        body: Map<String, String> = mapOf("permission" to "public", "block_downloads" to "false", "canEdit" to "false")
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

    fun addFileToFavorites(file: File, userDrive: UserDrive? = null, onSuccess: (() -> Unit)? = null) =
        liveData(Dispatchers.IO) {
            with(ApiRepository.postFavoriteFile(file)) {
                emit(this)

                if (isSuccess()) {
                    FileController.updateFile(file.id, userDrive = userDrive) {
                        it.isFavorite = true
                    }
                    onSuccess?.invoke()
                }
            }
        }

    fun deleteFileFromFavorites(file: File, userDrive: UserDrive? = null, onSuccess: ((File) -> Unit)? = null) =
        liveData(Dispatchers.IO) {
            with(ApiRepository.deleteFavoriteFile(file)) {
                emit(this)

                if (isSuccess()) {
                    FileController.updateFile(file.id, userDrive = userDrive) {
                        it.isFavorite = false
                        onSuccess?.invoke(it)
                    }
                }
            }
        }

    fun getFileDetails(fileId: Int, userDrive: UserDrive): LiveData<File?> {
        getFileDetailsJob.cancel()
        getFileDetailsJob = Job()
        return liveData(Dispatchers.IO + getFileDetailsJob) {
            emit(FileController.getFileDetails(fileId, userDrive))
        }
    }

    fun moveFile(file: File, newParent: File, onSuccess: ((fileId: Int) -> Unit)? = null) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.moveFile(file, newParent)
        if (apiResponse.isSuccess()) {
            FileController.getRealmInstance().use { realm ->
                file.getStoredFile(getContext())?.let { if (it.exists()) it.delete() }
                FileController.removeFile(file.id, recursive = false, customRealm = realm)
                FileController.updateFile(newParent.id, realm) { localFolder ->
                    file.isOffline = false
                    // Ignore expired transactions when it's suspended
                    // In case the phone is slow or in standby, the transaction can create an IllegalStateException
                    // because realm will not be available anymore, the transaction is resumed afterwards
                    // so we ignore the cases where it fails.
                    runCatching { localFolder.children.add(file) }
                }
            }

            onSuccess?.invoke(file.id)
        }
        emit(apiResponse)
    }

    fun renameFile(file: File, newName: String) = liveData(Dispatchers.IO) {
        emit(FileController.renameFile(file, newName))
    }

    fun updateFolderColor(file: File, color: String) = liveData(Dispatchers.IO) {
        emit(FileController.updateFolderColor(file, color))
    }

    fun deleteFile(file: File, userDrive: UserDrive? = null, onSuccess: ((fileId: Int) -> Unit)? = null) =
        liveData(Dispatchers.IO) {
            emit(FileController.deleteFile(file, userDrive = userDrive, context = getContext(), onSuccess = onSuccess))
        }

    fun restoreTrashFile(file: File, newFolderId: Int? = null, onSuccess: (() -> Unit)? = null) = liveData(Dispatchers.IO) {
        val body = newFolderId?.let { mapOf("destination_directory_id" to it) }
        with(ApiRepository.postRestoreTrashFile(file, body)) {
            emit(this)
            if (isSuccess()) onSuccess?.invoke()
        }
    }

    fun deleteTrashFile(file: File, onSuccess: (() -> Unit)? = null) = liveData(Dispatchers.IO) {
        with(ApiRepository.deleteTrashFile(file)) {
            emit(this)
            if (isSuccess()) onSuccess?.invoke()
        }
    }

    fun duplicateFile(
        file: File,
        folderId: Int? = null,
        copyName: String?,
        onSuccess: ((apiResponse: ApiResponse<File>) -> Unit)? = null,
    ) = liveData(Dispatchers.IO) {
        ApiRepository.duplicateFile(file, copyName, folderId ?: Utils.ROOT_ID).let { apiResponse ->
            if (apiResponse.isSuccess()) onSuccess?.invoke(apiResponse)
            emit(apiResponse)
        }
    }

    fun convertFile(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.convertFile(file))
    }

    fun observeDownloadOffline(context: Context) = WorkManager.getInstance(context).getWorkInfosLiveData(
        WorkQuery.Builder
            .fromUniqueWorkNames(arrayListOf(DownloadWorker.TAG))
            .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
            .build()
    )

    suspend fun removeOfflineFile(
        file: File,
        offlineFile: java.io.File,
        cacheFile: java.io.File,
        userDrive: UserDrive = UserDrive()
    ) = withContext(Dispatchers.IO) {
        FileController.updateOfflineStatus(file.id, false)
        if (file.isMedia()) file.deleteInMediaScan(getContext(), userDrive)
        if (cacheFile.exists()) cacheFile.delete()
        if (offlineFile.exists()) {
            offlineFile.delete()
        }
    }

    suspend fun syncOfflineFiles() {
        syncOfflineFilesJob.cancel()
        syncOfflineFilesJob = Job()
        runInterruptible(Dispatchers.IO + syncOfflineFilesJob) {
            SyncOfflineUtils.startSyncOffline(getContext(), syncOfflineFilesJob)
        }
    }

    @Deprecated(message = "Only for API 29 and below, otherwise use MediaStore.createDeleteRequest()")
    fun deleteSynchronizedFilesOnDevice(filesToDelete: ArrayList<UploadFile>) = viewModelScope.launch(Dispatchers.IO) {
        val fileDeleted = arrayListOf<UploadFile>()
        filesToDelete.forEach { uploadFile ->
            val uri = uploadFile.getUriObject()
            if (!uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                try {
                    SyncUtils.checkDocumentProviderPermissions(getContext(), uri)
                    val query = getContext().contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                    query?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            var columnIndex: Int? = null
                            var pathname: String? = null
                            try {
                                columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                                pathname = cursor.getString(columnIndex)
                                java.io.File(pathname).delete()
                                getContext().contentResolver.delete(uri, null, null)
                                fileDeleted.add(uploadFile)
                            } catch (nullPointerException: NullPointerException) {
                                Sentry.withScope { scope ->
                                    scope.setExtra("columnIndex", columnIndex.toString())
                                    scope.setExtra("pathname", pathname.toString())
                                    scope.setExtra("uploadFileUri", uploadFile.uri)
                                    Sentry.captureException(Exception("deleteSynchronizedFilesOnDevice()"))
                                }
                            }
                        }
                    }
                } catch (exception: SecurityException) {
                    exception.printStackTrace()
                    fileDeleted.add(uploadFile)
                }
            }
        }
        UploadFile.deleteAll(fileDeleted)
    }

    override fun onCleared() {
        realm.close()
        super.onCleared()
    }
}
