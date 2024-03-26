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
package com.infomaniak.drive.ui

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import androidx.collection.arrayMapOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.gson.JsonObject
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFilePermission
import com.infomaniak.drive.data.models.file.FileExternalImport.FileExternalImportStatus
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.addFiles.UploadFilesHelper
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.SyncUtils.isSyncScheduled
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SingleLiveEvent
import io.realm.Realm
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class MainViewModel(
    appContext: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(appContext) {

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

    val navigateFileListTo = SingleLiveEvent<File>()

    val deleteFileFromHome = SingleLiveEvent<Boolean>()
    val refreshActivities = SingleLiveEvent<Boolean>()
    val updateOfflineFile = SingleLiveEvent<FileId>()
    val updateVisibleFiles = MutableLiveData<Boolean>()

    var mustOpenUploadShortcut: Boolean
        get() = savedStateHandle[SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY] ?: true
        set(value) {
            savedStateHandle[SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY] = value
        }

    var ignoreSyncOffline = false

    var uploadFilesHelper: UploadFilesHelper? = null

    private var getFileDetailsJob = Job()
    private var syncOfflineFilesJob = Job()

    private fun getContext() = getApplication<MainApplication>()

    fun setCurrentFolder(folder: File?) {
        folder?.let {
            saveCurrentFolderId()
            uploadFilesHelper?.setParentFolder(it)
            currentFolder.value = it
        }
    }

    fun initUploadFilesHelper(fragmentActivity: FragmentActivity, navController: NavController) {
        uploadFilesHelper = UploadFilesHelper(
            activity = fragmentActivity,
            navController = navController,
            onOpeningPicker = {
                fragmentActivity.trackNewElementEvent("uploadFile")
                uploadFilesHelper?.let(::setParentFolder) ?: Sentry.captureMessage("UploadFilesHelper is null. It should not!")
            },
        ).apply {
            initCurrentFolderFromRealm()
            setParentFolder(uploadFilesHelper = this)
        }
    }

    fun saveMustOpenUploadShortcut(mustOpenShortcut: Boolean) {
        savedStateHandle[SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY] = mustOpenShortcut
    }

    fun navigateFileListTo(navController: NavController, fileId: Int) {
        // Clear FileListFragment stack
        with(navController) {
            popBackStack(R.id.homeFragment, false)
            navigate(R.id.fileListFragment)
        }

        if (fileId == Utils.ROOT_ID) return

        // Emit destination folder id
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileController.getFileById(fileId) ?: FileController.getFileDetails(fileId) ?: return@launch
            if (fileId > Utils.ROOT_ID) navigateFileListTo.postValue(file)
        }
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

    fun createShareLink(file: File) = liveData(Dispatchers.IO) {
        val body = ShareLink().ShareLinkSettings(right = ShareLinkFilePermission.PUBLIC, canDownload = true, canEdit = false)
        val apiResponse = ApiRepository.createShareLink(file, body)

        if (apiResponse.isSuccess()) {
            FileController.updateFile(file.id) { it.sharelink = apiResponse.data }
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

        with(ApiRepository.postDropBox(file, body)) {
            if (isSuccess()) FileController.updateDropBox(file.id, data)
            emit(this)
        }
    }

    fun updateDropBox(file: File, newDropBox: DropBox) = liveData(Dispatchers.IO) {
        val data = JsonObject().apply {
            addProperty("email_when_finished", newDropBox.newHasNotification)
            addProperty("valid_until", newDropBox.newValidUntil?.time?.let { it / 1000 })
            addProperty("limit_file_size", newDropBox.newLimitFileSize)

            if (newDropBox.newPassword && !newDropBox.newPasswordValue.isNullOrBlank()) {
                addProperty("password", newDropBox.newPasswordValue)
            } else if (!newDropBox.newPassword) {
                val password: String? = null
                addProperty("password", password)
            }
        }
        with(ApiRepository.updateDropBox(file, data)) {
            if (isSuccess()) FileController.updateDropBox(file.id, newDropBox)
            emit(this)
        }
    }

    fun deleteDropBox(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteDropBox(file))
    }

    fun deleteFileShareLink(file: File) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.deleteFileShareLink(file)
        if (apiResponse.isSuccess()) FileController.updateFile(file.id) {
            it.sharelink = null
            it.rights?.canBecomeShareLink = true
        }
        emit(apiResponse)
    }

    fun getShareLink(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.getShareLink(file))
    }

    fun getFileShare(fileId: Int, userDrive: UserDrive? = null) = liveData(Dispatchers.IO) {
        val okHttpClient = userDrive?.userId?.let { AccountUtils.getHttpClient(it) } ?: HttpClient.okHttpClient
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
                file.getStoredFile(getContext())?.let { ioFile ->
                    if (ioFile.exists()) moveIfOfflineFileOrDelete(file, ioFile, newParent)
                }

                FileController.updateFile(file.parentId, realm) { localFolder ->
                    // Ignore expired transactions when it's suspended
                    // In case the phone is slow or in standby, the transaction can create an IllegalStateException
                    // because realm will not be available anymore, the transaction is resumed afterwards
                    // so we ignore the cases where it fails.
                    runCatching { localFolder.children.remove(file) }
                }

                FileController.addChild(newParent.id, file.apply { parentId = newParent.id }, realm)
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

    fun manageCategory(categoryId: Int, files: List<File>, isAdding: Boolean) = liveData(Dispatchers.IO) {
        with(manageCategoryApiCall(files, categoryId, isAdding)) {
            data?.forEach { feedbackResource ->
                if (feedbackResource.result) {
                    FileController.updateFile(feedbackResource.id) {
                        if (isAdding) {
                            it.categories.add(FileCategory(categoryId, userId = AccountUtils.currentUserId, addedAt = Date()))
                        } else {
                            it.categories.find(categoryId)?.deleteFromRealm()
                        }
                    }
                }
            }

            emit(this)
        }
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

    fun copyFile(
        file: File,
        destinationId: Int? = null,
        copyName: String?,
        onSuccess: ((apiResponse: ApiResponse<File>) -> Unit)? = null,
    ) = liveData(Dispatchers.IO) {
        ApiRepository.copyFile(file, copyName, destinationId ?: Utils.ROOT_ID).let { apiResponse ->
            if (apiResponse.isSuccess()) onSuccess?.invoke(apiResponse)
            emit(apiResponse)
        }
    }

    fun duplicateFile(
        file: File,
        copyName: String?,
        onSuccess: ((apiResponse: ApiResponse<File>) -> Unit)? = null,
    ) = liveData(Dispatchers.IO) {
        ApiRepository.duplicateFile(file, copyName).let { apiResponse ->
            if (apiResponse.isSuccess()) onSuccess?.invoke(apiResponse)
            emit(apiResponse)
        }
    }

    fun convertFile(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.convertFile(file))
    }

    fun cancelExternalImport(importId: Int) = liveData(Dispatchers.IO) {
        val driveId = AccountUtils.currentDriveId
        val apiResponse = ApiRepository.cancelExternalImport(driveId, importId)

        if (apiResponse.isSuccess()) {
            FileController.updateExternalImportStatus(driveId, importId, FileExternalImportStatus.CANCELING)
        }

        emit(apiResponse)
    }

    fun observeDownloadOffline(context: Context) = WorkManager.getInstance(context).getWorkInfosLiveData(
        WorkQuery.Builder
            .fromUniqueWorkNames(arrayListOf(DownloadWorker.TAG))
            .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
            .build()
    )

    suspend fun restartUploadWorkerIfNeeded() = withContext(Dispatchers.IO) {
        if (UploadFile.getAllPendingUploadsCount() > 0 && !getContext().isSyncScheduled()) {
            getContext().syncImmediately()
        }
    }

    suspend fun removeOfflineFile(
        file: File,
        offlineFile: IOFile,
        cacheFile: IOFile,
        userDrive: UserDrive = UserDrive()
    ) = withContext(Dispatchers.IO) {
        FileController.updateOfflineStatus(file.id, false)
        if (file.isMedia()) file.deleteInMediaScan(getContext(), userDrive)
        if (cacheFile.exists()) cacheFile.delete()
        if (offlineFile.exists()) {
            offlineFile.delete()
        }
    }

    fun syncOfflineFiles() {
        syncOfflineFilesJob.cancel()
        syncOfflineFilesJob = Job()
        viewModelScope.launch(Dispatchers.IO + syncOfflineFilesJob) {
            SyncOfflineUtils.startSyncOffline(getContext(), syncOfflineFilesJob)
        }
    }

    @Deprecated(message = "Only for API 29 and below, otherwise use MediaStore.createDeleteRequest()")
    fun deleteSynchronizedFilesOnDevice(filesToDelete: ArrayList<UploadFile>) = viewModelScope.launch(Dispatchers.IO) {
        val fileDeleted = arrayListOf<UploadFile>()
        filesToDelete.forEach { uploadFile ->
            try {
                val uri = uploadFile.getUriObject()
                val query = getContext().contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                query?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        var columnIndex: Int? = null
                        var pathname: String? = null
                        try {
                            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            pathname = cursor.getString(columnIndex)
                            IOFile(pathname).delete()
                            getContext().contentResolver.delete(uri, null, null)
                        } catch (nullPointerException: NullPointerException) {
                            Sentry.withScope { scope ->
                                scope.setExtra("columnIndex", columnIndex.toString())
                                scope.setExtra("pathname", pathname.toString())
                                scope.setExtra("uploadFileUri", uploadFile.uri)
                                Sentry.captureException(Exception("deleteSynchronizedFilesOnDevice()"))
                            }
                        } finally {
                            fileDeleted.add(uploadFile)
                        }
                    }
                } ?: fileDeleted.add(uploadFile)
            } catch (exception: SecurityException) {
                Sentry.captureException(exception)
                exception.printStackTrace()
                fileDeleted.add(uploadFile)
            }
        }
        UploadFile.deleteAll(fileDeleted)
    }

    private fun moveIfOfflineFileOrDelete(file: File, ioFile: IOFile, newParent: File) {
        if (file.isOffline) ioFile.renameTo(IOFile("${newParent.getRemotePath()}/${file.name}"))
        else ioFile.delete()
    }

    private fun setParentFolder(uploadFilesHelper: UploadFilesHelper) {
        currentFolder.value?.let {
            saveCurrentFolderId()
            uploadFilesHelper.setParentFolder(it)
        } ?: {
            initCurrentFolderFromRealm()
            saveCurrentFolderId()
            uploadFilesHelper.setParentFolder(currentFolder.value!!)
        }
    }

    private fun manageCategoryApiCall(
        files: List<File>,
        categoryId: Int,
        isAdding: Boolean,
    ): ApiResponse<List<ShareableItems.FeedbackAccessResource<Int, Unit>>> {
        return if (isAdding) ApiRepository.addCategory(files, categoryId) else ApiRepository.removeCategory(files, categoryId)
    }

    private fun saveCurrentFolderId() {
        currentFolder.value?.let { savedStateHandle[SAVED_STATE_FOLDER_ID_KEY] = it.id }
    }

    private fun initCurrentFolderFromRealm() {
        val savedFolderId = savedStateHandle.get<Int>(SAVED_STATE_FOLDER_ID_KEY)
        if (currentFolder.value == null && savedFolderId != null) {
            FileController.getFileById(savedStateHandle[SAVED_STATE_FOLDER_ID_KEY]!!)?.let {
                this.currentFolder.value = it
                uploadFilesHelper?.setParentFolder(it)
            }
        }
    }

    override fun onCleared() {
        realm.close()
        super.onCleared()
    }

    companion object {
        private const val SAVED_STATE_FOLDER_ID_KEY = "folderId"
        private const val SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY = "mustOpenUploadShortcut"
    }
}
