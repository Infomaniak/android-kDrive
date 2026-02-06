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
package com.infomaniak.drive.ui

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import androidx.collection.MutableIntList
import androidx.collection.mutableIntListOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.infomaniak.core.auth.networking.HttpClient
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.NetworkAvailability
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FolderFilesProvider
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.FileListNavigationType
import com.infomaniak.drive.data.models.ShareableItems.FeedbackAccessResource
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.deeplink.DeeplinkAction
import com.infomaniak.drive.data.models.file.FileExternalImport.FileExternalImportStatus
import com.infomaniak.drive.data.models.file.SpecialFolder.Trash
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.addFiles.UploadFilesHelper
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.FileId
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.SyncOfflineUtils
import com.infomaniak.drive.utils.SyncUtils.isSyncScheduled
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.find
import io.realm.Realm
import io.realm.kotlin.toFlow
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
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

    private val privateFolder = MutableLiveData<File>()
    private val _currentFolder = MutableLiveData<File?>()
    val currentFolder: LiveData<File?> = _currentFolder // Use `setCurrentFolder` and `postCurrentFolder` to set value on it

    val currentFolderOpenAddFileBottom = MutableLiveData<File>()
    var currentPreviewFileList = LinkedHashMap<Int, File>()

    private val _pendingUploadsCount = MutableLiveData<Int?>(null)

    val navigateFileListTo = SingleLiveEvent<FileListNavigationType>()
    val navigateDeeplink = MutableStateFlow<DeeplinkAction.Drive?>(null)

    val deleteFileFromHome = SingleLiveEvent<Boolean>()
    val refreshActivities = SingleLiveEvent<Boolean>()
    val updateOfflineFile = SingleLiveEvent<FileId>()
    val updateVisibleFiles = MutableLiveData<Boolean>()
    val isBulkDownloadRunning = MutableLiveData<Boolean>()

    val isNetworkAvailable = NetworkAvailability(this@MainViewModel.getContext()).isNetworkAvailable.distinctUntilChanged()
    var hasNetwork: Boolean = true
        private set

    var mustOpenUploadShortcut: Boolean = true
        get() = savedStateHandle[SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY] ?: field
        set(value) {
            savedStateHandle[SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY] = value
            field = value
        }

    var ignoreSyncOffline = false

    var uploadFilesHelper: UploadFilesHelper? = null

    private var rootFilesJob: Job = Job()
    private var getFileDetailsJob = Job()
    private var syncOfflineFilesJob: Job? = null
    private var setCurrentFolderJob = Job()

    val deleteFilesFromGallery = SingleLiveEvent<List<Int>>()

    init {
        viewModelScope.launch {
            isNetworkAvailable.collect {
                onNetworkAvailabilityChanged(it)
                hasNetwork = it
            }
        }
    }

    private fun getContext() = getApplication<MainApplication>()

    fun setCurrentFolder(folder: File?) {
        folder?.let {
            setCurrentFolderJob.cancel()
            saveCurrentFolderId()
            uploadFilesHelper?.setParentFolder(it)
            _currentFolder.value = it
        }
    }

    fun setCurrentFolderAsRoot(): Job {
        setCurrentFolderJob.cancel()
        setCurrentFolderJob = Job()
        return viewModelScope.launch(Dispatchers.IO + setCurrentFolderJob) {
            val file = privateFolder.value ?: FileController.getPrivateFolder().also { privateFolder.postValue(it) }
            setCurrentFolderJob.ensureActive()
            _currentFolder.postValue(file)
        }
    }

    private fun postCurrentFolder(file: File?) {
        setCurrentFolderJob.cancel()
        _currentFolder.postValue(file)
    }

    fun initUploadFilesHelper(fragmentActivity: FragmentActivity, navController: NavController) {
        uploadFilesHelper = UploadFilesHelper(
            activity = fragmentActivity,
            navController = navController,
            onOpeningPicker = {
                trackNewElementEvent(MatomoName.UploadFile)
                uploadFilesHelper?.let { setParentFolder() } ?: Sentry.captureMessage("UploadFilesHelper is null. It should not!")
            },
        )
        initCurrentFolderFromRealm()
        setParentFolder()
    }

    fun loadRootFiles() {
        rootFilesJob.cancel()
        rootFilesJob = viewModelScope.launch {
            FolderFilesProvider.loadRootFiles(UserDrive(), hasNetwork)
        }
    }

    fun navigateFileListTo(navController: NavController, fileId: Int, userDrive: UserDrive, subfolderId: Int?) {
        // Clear FileListFragment stack
        navController.popBackStack(R.id.rootFilesFragment, false)

        if (fileId <= Utils.ROOT_ID) {
            if (fileId == Trash.id) {
                subfolderId?.let {
                    navigateFileListTo.postValue(FileListNavigationType.Subfolder(Trash.file, it))
                } ?: run {
                    navigateFileListTo.postValue(FileListNavigationType.Folder(Trash.file))
                }
            }
            return // Deeplinks could lead us to navigating to the true root
        }

        // Emit destination folder id
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileController.getFileById(fileId, userDrive)
                ?: FileController.getFileDetails(fileId, userDrive = userDrive)
                ?: return@launch
            navigateFileListTo.postValue(FileListNavigationType.Folder(file))
        }
    }

    fun loadCurrentFolder(folderId: Int, userDrive: UserDrive) = viewModelScope.launch(Dispatchers.IO) {
        postCurrentFolder(FileController.getFileById(folderId, userDrive))
    }

    fun createMultiSelectMediator(): MediatorLiveData<MultiSelectMediatorState> =
        MediatorLiveData<MultiSelectMediatorState>().apply {
            value = MultiSelectMediatorState(numberOfSuccessfulActions = 0, totalOfActions = 0, errorCode = null)
        }

    fun updateMultiSelectMediator(mediator: MediatorLiveData<MultiSelectMediatorState>): (FileResult) -> Unit = { fileRequest ->
        var numberOfSuccessfulActions = mediator.value!!.numberOfSuccessfulActions
        if (fileRequest.isSuccess) numberOfSuccessfulActions++

        val totalOfActions = mediator.value!!.totalOfActions + 1

        mediator.value = MultiSelectMediatorState(
            numberOfSuccessfulActions,
            totalOfActions,
            fileRequest.errorCode,
        )
    }

    fun getFileShare(fileId: Int, userDrive: UserDrive? = null) = liveData(Dispatchers.IO) {
        val okHttpClient = userDrive?.userId?.let {
            AccountUtils.getHttpClient(it)
        } ?: HttpClient.okHttpClientWithTokenInterceptor

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
                emit(FileResult(this.isSuccess()))

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
                emit(FileResult(this.isSuccess()))

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

    fun moveFile(
        file: File,
        newParent: File,
        isSharedWithMe: Boolean = false,
        onSuccess: ((fileId: Int) -> Unit)? = null
    ) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.moveFile(file, newParent)
        if (apiResponse.isSuccess()) {
            FileController.getRealmInstance().use { currentDriveRealm ->
                file.getStoredFile(getContext())?.let { ioFile ->
                    if (ioFile.exists()) moveIfOfflineFileOrDelete(file, ioFile, newParent)
                }

                FileController.updateFile(file.parentId, currentDriveRealm) { localFolder ->
                    // Ignore expired transactions when it's suspended
                    // In case the phone is slow or in standby, the transaction can create an IllegalStateException
                    // because realm will not be available anymore, the transaction is resumed afterwards
                    // so we ignore the cases where it fails.
                    runCatching { localFolder.children.remove(file) }
                }

                if (isSharedWithMe) {
                    // If the selected folder is a shared folder, it is removed from the user's realm table (userId_driveId)
                    // and added to the share realm (userId_share)
                    FileController.removeFile(fileId = file.id, customRealm = currentDriveRealm)
                    FileController.getRealmInstance(UserDrive(sharedWithMe = true)).use { sharedRealm ->
                        FileController.updateFile(newParent.id, sharedRealm) { localFolder ->
                            runCatching { localFolder.children.add(file) }.onFailure(Sentry::captureException)
                        }
                    }
                } else {
                    FileController.addChild(newParent.id, file.apply { parentId = newParent.id }, currentDriveRealm)
                }
            }

            onSuccess?.invoke(file.id)
        }

        emit(FileResult(isSuccess = apiResponse.isSuccess(), errorCode = apiResponse.error?.code))
    }

    fun renameFile(file: File, newName: String) = liveData(Dispatchers.IO) {
        emit(FileController.renameFile(file, newName))
    }

    fun updateFolderColor(file: File, color: String, userDrive: UserDrive) = liveData(Dispatchers.IO) {
        emit(FileResult(isSuccess = FileController.updateFolderColor(file, color, userDrive).isSuccess()))
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
            with(FileController.deleteFile(file, userDrive = userDrive, context = getContext(), onSuccess = onSuccess)) {
                emit(
                    FileResult(
                        isSuccess = this.isSuccess(),
                        data = this.data,
                        errorCode = this.error?.code,
                        errorResId = this.translateError()
                    )
                )
            }
        }

    fun restoreTrashFile(file: File, newFolderId: Int? = null, onSuccess: (() -> Unit)? = null) = liveData(Dispatchers.IO) {
        val body = newFolderId?.let { mapOf("destination_directory_id" to it) }
        with(ApiRepository.postRestoreTrashFile(file, body)) {
            emit(FileResult(this.isSuccess(), errorCode = this.error?.code))
            if (isSuccess()) onSuccess?.invoke()
        }
    }

    fun deleteTrashFile(file: File, onSuccess: (() -> Unit)? = null) = liveData(Dispatchers.IO) {
        with(ApiRepository.deleteTrashFile(file)) {
            emit(FileResult(this.isSuccess()))
            if (isSuccess()) onSuccess?.invoke()
        }
    }

    fun duplicateFile(
        file: File,
        destinationId: Int? = null,
        onSuccess: ((apiResponse: ApiResponse<File>) -> Unit)? = null,
    ) = liveData(Dispatchers.IO) {
        ApiRepository.duplicateFile(file, destinationId ?: Utils.ROOT_ID).let { apiResponse ->
            if (apiResponse.isSuccess()) onSuccess?.invoke(apiResponse)
            emit(FileResult(isSuccess = apiResponse.isSuccess(), data = apiResponse.data, errorCode = apiResponse.error?.code))
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingUploadsCount: LiveData<Int> = _pendingUploadsCount.switchMap { folderId ->
        UploadFile.getCurrentUserPendingUploadFile(folderId)
            .toFlow()
            .mapLatest { list -> list.count() }
            .distinctUntilChanged()
            .cancellable()
            .asLiveData()
    }

    fun observeDownloadOffline(context: Context) = WorkManager.getInstance(context).getWorkInfosLiveData(
        WorkQuery.Builder
            .fromUniqueWorkNames(arrayListOf(DownloadWorker.TAG))
            .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
            .build()
    )

    fun restartUploadWorkerIfNeeded() {
        viewModelScope.launch {
            if (UploadFile.getAllPendingUploadsCount() > 0 && !getContext().isSyncScheduled()) {
                getContext().syncImmediately()
            }
        }
    }

    fun removeSelectedFilesFromOffline(files: List<File>, onSuccess: (() -> Unit)? = null) = liveData {
        val filesId = files.map {
            val file: File = it.freeze()
            if (!file.isFolder()) {
                val offlineFile = file.getOfflineFile(getApplication())
                val cacheFile = file.getCacheFile(getApplication())
                if (file.isOffline && offlineFile != null) {
                    deleteFile(file, offlineFile, cacheFile)
                }
            }
            file.id
        }

        viewModelScope.launch(Dispatchers.IO) {
            FileController.updateIsOfflineForFiles(fileIds = filesId, isOffline = false)
            onSuccess?.invoke()
            emit(FileResult(isSuccess = true))
        }
    }

    fun removeOfflineFile(
        file: File,
        offlineFile: IOFile,
        cacheFile: IOFile,
        userDrive: UserDrive = UserDrive(),
        onFileRemovedFromOffline: (() -> Unit)? = null,
    ) {
        // We need to call this method outside the UI thread
        viewModelScope.launch(Dispatchers.IO) {
            FileController.updateOfflineStatus(file.id, isOffline = false)
        }
        deleteFile(file, offlineFile, cacheFile, userDrive, onFileRemovedFromOffline)
    }

    private fun deleteFile(
        file: File,
        offlineFile: IOFile,
        cacheFile: IOFile,
        userDrive: UserDrive = UserDrive(),
        onFileRemovedFromOffline: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            if (file.isMedia()) file.deleteInMediaScan(getContext(), userDrive)
            if (cacheFile.exists()) cacheFile.delete()
            if (offlineFile.exists()) {
                offlineFile.delete()
            }
            onFileRemovedFromOffline?.invoke()
        }
    }

    fun syncOfflineFiles() {
        syncOfflineFilesJob?.cancel()
        syncOfflineFilesJob = viewModelScope.launch(Dispatchers.IO) {
            SyncOfflineUtils.startSyncOffline(getContext())
        }
    }

    fun cancelSyncOfflineFiles() {
        syncOfflineFilesJob?.cancel()
    }

    // Only for API 29 and below, otherwise use MediaStore.createDeleteRequest()
    fun deleteSynchronizedFilesOnDevice(filesToDelete: List<UploadFile>) = viewModelScope.launch(Dispatchers.IO) {
        val fileDeleted: MutableList<UploadFile> = mutableListOf()
        val isIOFilesDeleted: MutableList<Boolean> = mutableListOf()
        val fileDeleteContentResolver: MutableIntList = mutableIntListOf()
        var inconsistenciesCount = 0
        val tag = "deleteSynchronizedFilesOnDevice"

        SentryLog.i(tag, "filesToDelete (size): ${filesToDelete.size}")
        filesToDelete.forEach { uploadFile ->
            try {
                val uri = uploadFile.getUriObject()
                val query = getContext().contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                query?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        var columnIndex: Int? = null
                        try {
                            columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            val pathname = cursor.getString(columnIndex)

                            isIOFilesDeleted.add(IOFile(pathname).delete())
                            fileDeleteContentResolver.add(getContext().contentResolver.delete(uri, null, null))
                        } catch (_: NullPointerException) {
                            Sentry.captureException(Exception("deleteSynchronizedFilesOnDevice()")) { scope ->
                                scope.setExtra("columnIndex", columnIndex.toString())
                            }
                        } finally {
                            fileDeleted.add(uploadFile)
                        }
                    } else {
                        // The app was killed before updating `deleteAt` in the realm db
                        inconsistenciesCount++
                        fileDeleted.add(uploadFile)
                    }
                } ?: fileDeleted.add(uploadFile)
            } catch (exception: SecurityException) {
                Sentry.captureException(exception)
                exception.printStackTrace()
                fileDeleted.add(uploadFile)
            }
        }
        SentryLog.i(tag, "isIOFilesDeleted[${isIOFilesDeleted.size}]: $isIOFilesDeleted")
        SentryLog.i(tag, "fileDeleteContentResolver[${fileDeleteContentResolver.size}]: $fileDeleteContentResolver")
        SentryLog.i(tag, "fileDeleted size after increment: ${fileDeleted.size}")
        SentryLog.i(tag, "file doesn't exist on device but deleteAt isn't up to date in the realm db: $inconsistenciesCount")
        Sentry.captureMessage("End deleteSynchronizedFilesOnDevice. Nb of error $inconsistenciesCount")

        UploadFile.deleteAll(fileDeleted)
    }

    fun checkBulkDownloadStatus() = viewModelScope.launch {
        val isRunning = DownloadOfflineFileManager.isBulkDownloadWorkerRunning(getContext())
        isBulkDownloadRunning.value = isRunning
        ignoreSyncOffline = isRunning
    }

    fun markFilesAsOffline(filesId: List<Int>, isMarkedAsOffline: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        FileController.getRealmInstance().use { realm ->
            FileController.markFilesAsOffline(customRealm = realm, filesId = filesId, isMarkedAsOffline = isMarkedAsOffline)
        }
    }

    private suspend fun onNetworkAvailabilityChanged(isNetworkAvailable: Boolean) {
        SentryLog.d("Internet availability", if (isNetworkAvailable) "Available" else "Unavailable")
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Network"
            message = "Internet access is available : $isNetworkAvailable"
            level = if (isNetworkAvailable) SentryLevel.INFO else SentryLevel.WARNING
        })
        if (isNetworkAvailable) {
            AccountUtils.updateCurrentUserAndDrives(this@MainViewModel.getContext())
            restartUploadWorkerIfNeeded()
        }
    }

    private fun moveIfOfflineFileOrDelete(file: File, ioFile: IOFile, newParent: File) {
        if (file.isOffline) {
            ioFile.renameTo(IOFile("${newParent.getRemotePath()}/${file.name}"))
        } else {
            ioFile.delete()
        }
    }

    private fun saveCurrentFolder() {
        saveCurrentFolderId()
        uploadFilesHelper?.setParentFolder(currentFolder.value!!)
    }

    private fun setParentFolder() {
        currentFolder.value?.let {
            saveCurrentFolder()
        } ?: run {
            initCurrentFolderFromRealm()
        }
    }

    private fun manageCategoryApiCall(
        files: List<File>,
        categoryId: Int,
        isAdding: Boolean,
    ): ApiResponse<List<FeedbackAccessResource<Int, Unit>>> {
        return if (isAdding) ApiRepository.addCategory(files, categoryId) else ApiRepository.removeCategory(files, categoryId)
    }

    private fun saveCurrentFolderId() {
        currentFolder.value?.let { savedStateHandle[SAVED_STATE_FOLDER_ID_KEY] = it.id }
    }

    private fun initCurrentFolderFromRealm() {
        val savedFolderId: Int? = savedStateHandle[SAVED_STATE_FOLDER_ID_KEY]
        if (currentFolder.value == null && savedFolderId != null) {
            FileController.getFileById(savedFolderId)?.let {
                _currentFolder.value = it
                saveCurrentFolder()
            }
        }
    }

    fun switchToNextUser(onUserSwitched: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        if (AccountUtils.getAllUsersSync().size < 2) return@launch

        AccountUtils.switchToNextUser()

        withContext(Dispatchers.Main) { onUserSwitched() }
    }

    override fun onCleared() {
        realm.close()
        super.onCleared()
    }

    data class FileResult(
        val isSuccess: Boolean,
        val errorResId: Int? = null,
        val data: Any? = null,
        val errorCode: String? = null
    )

    data class MultiSelectMediatorState(
        var numberOfSuccessfulActions: Int,
        var totalOfActions: Int,
        var errorCode: String?,
    )

    companion object {
        const val TAG = "MainViewModel"

        private const val SAVED_STATE_FOLDER_ID_KEY = "folderId"
        private const val SAVED_STATE_MUST_OPEN_UPLOAD_SHORTCUT_KEY = "mustOpenUploadShortcut"
    }
}
