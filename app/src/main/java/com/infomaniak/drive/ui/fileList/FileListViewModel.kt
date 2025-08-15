/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.cancellable
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FolderFilesProvider
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.Type
import com.infomaniak.drive.data.models.FileCount
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FileId
import com.infomaniak.drive.utils.Position
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SingleLiveEvent
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FileListViewModel(application: Application) : AndroidViewModel(application) {

    private inline val context get() = getApplication<MainApplication>().applicationContext
    private var realm: Realm? = null

    private var getFilesJob: Job = Job()
    private var getFolderActivitiesJob: Job = Job()
    private var checkOfflineFilesJob = Job()

    var hasNavigatedToLastVisitedFileTreeCategory = false

    lateinit var sortType: SortType

    var isSharedWithMe = false

    val isListMode = SingleLiveEvent<Boolean>()

    var lastItemCount: FileCount? = null

    private val loadRootFiles = MutableSharedFlow<UserDrive>(replay = 1)

    fun sortTypeIsInitialized() = ::sortType.isInitialized

    @OptIn(ExperimentalCoroutinesApi::class)
    val rootFiles: LiveData<Map<File.VisibilityType, File>> = loadRootFiles.distinctUntilChanged().flatMapLatest {
        FileController.getRealmInstance(it).run {
            realm = this
            FileController.getFolderFilesFlow(this, Utils.ROOT_ID)
        }
    }
        .mapLatest { it.associateBy(File::getVisibilityType) }
        .cancellable()
        .asLiveData(viewModelScope.coroutineContext)

    fun updateRootFiles(userDrive: UserDrive) {
        viewModelScope.launch {
            loadRootFiles.emit(userDrive)
        }
    }

    fun getFiles(
        folderId: Int,
        order: SortType,
        sourceRestrictionType: FolderFilesProvider.SourceRestrictionType,
        userDrive: UserDrive? = null,
        isNewSort: Boolean,
    ): LiveData<FolderFilesResult?> {
        getFilesJob.cancel()
        getFolderActivitiesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            tailrec suspend fun recursiveDownload(folderId: Int, isFirstPage: Boolean) {
                getFilesJob.ensureActive()

                val folderFilesProviderResult = FolderFilesProvider.getFiles(
                    FolderFilesProvider.FolderFilesProviderArgs(
                        folderId = folderId,
                        isFirstPage = isFirstPage,
                        order = order,
                        sourceRestrictionType = sourceRestrictionType,
                        userDrive = userDrive ?: UserDrive(),
                    )
                )

                when {
                    folderFilesProviderResult == null -> emit(null)
                    folderFilesProviderResult.isComplete -> {
                        emit(
                            FolderFilesResult(
                                parentFolder = folderFilesProviderResult.folder,
                                files = folderFilesProviderResult.folderFiles,
                                isComplete = true,
                                isFirstPage = isFirstPage,
                                isNewSort = isNewSort,
                            )
                        )
                    }
                    else -> {
                        if (isFirstPage) {
                            emit(
                                FolderFilesResult(
                                    parentFolder = folderFilesProviderResult.folder,
                                    files = folderFilesProviderResult.folderFiles,
                                    isComplete = true,
                                    isFirstPage = true,
                                    isNewSort = isNewSort,
                                )
                            )
                        }
                        recursiveDownload(folderId, isFirstPage = false)
                    }
                }
            }
            runCatching {
                recursiveDownload(folderId, isFirstPage = true)
            }.cancellable().onFailure { t ->
                SentryLog.e(TAG, "recursiveDownload failed", t)
            }.getOrNull()
        }
    }

    fun getFavoriteFiles(order: SortType, isNewSort: Boolean): LiveData<FolderFilesResult?> {
        getFilesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            tailrec suspend fun recursive(isFirstPage: Boolean, isNewSort: Boolean, cursor: String? = null) {
                getFilesJob.ensureActive()
                val apiResponse = ApiRepository.getFavoriteFiles(AccountUtils.currentDriveId, order, cursor)
                if (apiResponse.isSuccess()) {
                    when {
                        apiResponse.data.isNullOrEmpty() -> emit(null)
                        apiResponse.hasMoreAndCursorExists -> {
                            apiResponse.data?.let { FileController.saveFavoritesFiles(it, isFirstPage) }
                            emit(
                                FolderFilesResult(
                                    files = apiResponse.data!!,
                                    isComplete = false,
                                    isFirstPage = isFirstPage,
                                    isNewSort = isNewSort,
                                )
                            )
                            recursive(isFirstPage = false, isNewSort = false, cursor = apiResponse.cursor)
                        }
                        else -> {
                            FileController.saveFavoritesFiles(apiResponse.data!!, isFirstPage)
                            emit(
                                FolderFilesResult(
                                    files = apiResponse.data!!,
                                    isComplete = true,
                                    isFirstPage = isFirstPage,
                                    isNewSort = isNewSort,
                                )
                            )
                        }
                    }
                } else emit(
                    FolderFilesResult(
                        files = FileController.getFilesFromCache(FileController.FAVORITES_FILE_ID),
                        isComplete = true,
                        isFirstPage = true,
                        isNewSort = isNewSort,
                    )
                )
            }
            recursive(isFirstPage = true, isNewSort = isNewSort)
        }
    }

    fun getPendingFilesCount(folderId: Int) = liveData(Dispatchers.IO) {
        emit(UploadFile.getCurrentUserPendingUploadsCount(folderId))
    }

    fun getFileCount(folder: File): LiveData<FileCount> = liveData(Dispatchers.IO) {
        lastItemCount?.let { emit(it) }
        val apiResponse = if (folder.isPublicShared()) {
            ApiRepository.getPublicShareFileCount(folder.driveId, folder.publicShareUuid, folder.id)
        } else {
            ApiRepository.getFileCount(folder)
        }

        apiResponse.data?.let { fileCount ->
            lastItemCount = fileCount
            emit(fileCount)
        }
    }

    fun getMySharedFiles(sortType: SortType): LiveData<Pair<ArrayList<File>, Boolean>?> {
        getFilesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            FileController.getMySharedFiles(UserDrive(), sortType, transaction = { files, isComplete ->
                runBlocking { emit(files to isComplete) }
            })
        }
    }

    fun updateOfflineFilesIfNeeded(folder: File, files: List<File>) {
        if (folder.getOfflineFile(context)?.exists() == true && FileController.getFolderOfflineFilesCount(folder.id) == 0L) {
            checkOfflineFilesJob.cancel()
            checkOfflineFilesJob = Job()

            viewModelScope.launch(Dispatchers.IO + checkOfflineFilesJob) {
                FileController.getRealmInstance().use {
                    it.executeTransaction { realm ->
                        files.filterNot { file -> file.isFolder() }.forEach { file ->
                            if (!file.isOffline && file.getOfflineFile(context)?.exists() == true && isActive) {
                                FileController.getFileProxyById(file.id, customRealm = realm)?.isOffline = true
                            }
                        }
                    }
                }
            }
        }

    }

    @Synchronized
    fun getFolderActivities(folder: File, userDrive: UserDrive? = null): LiveData<Boolean> {
        getFolderActivitiesJob.cancel()
        getFolderActivitiesJob = Job()
        return liveData(Dispatchers.IO + getFolderActivitiesJob) {
            mutex.withLock {
                val activitiesAreLoadedWithSuccess = FolderFilesProvider.tryLoadActivitiesFromFolder(
                    folder = folder,
                    userDrive = userDrive ?: UserDrive(),
                    activitiesJob = getFolderActivitiesJob
                )
                emit(activitiesAreLoadedWithSuccess)
            }
        }
    }

    private var pendingJob = Job()
    val currentAdapterPendingFiles = MutableLiveData<ArrayList<File>>()
    val indexUploadToDelete = currentAdapterPendingFiles.switchMap { files ->
        val adapterPendingFileIds = files.map { it.id }
        val isFileType = files.firstOrNull()?.type == Type.FILE.value
        pendingFilesToDelete(adapterPendingFileIds, isFileType)
    }

    private fun pendingFilesToDelete(adapterPendingFileIds: List<Int>, isFileType: Boolean):
            LiveData<ArrayList<Pair<Position, FileId>>> {

        pendingJob.cancel()
        pendingJob = Job()

        return liveData(Dispatchers.IO + pendingJob) {
            val positions = arrayListOf<Pair<Position, FileId>>()
            UploadFile.getRealmInstance().use { uploadRealm ->
                val realmUploadFiles =
                    if (isFileType) UploadFile.getAllPendingUploads(customRealm = uploadRealm)
                    else UploadFile.getAllPendingFolders(realm = uploadRealm)

                adapterPendingFileIds.forEachIndexed { index, fileId ->
                    pendingJob.ensureActive()
                    val uploadExists = realmUploadFiles?.any { uploadFile ->
                        isFileType && fileId == uploadFile.uri.hashCode() || !isFileType && fileId == uploadFile.remoteFolder
                    }
                    if (uploadExists == false) positions.add(index to fileId)
                }

                pendingJob.ensureActive()
            }
            emit(positions)
        }
    }

    fun updateExternalImport(notification: MqttNotification) {
        viewModelScope.launch(Dispatchers.IO) {
            with(notification) {
                if (importId != null && action != null) FileController.updateExternalImport(driveId, importId, action)
            }
        }
    }

    fun cancelDownloadFiles() {
        pendingJob.cancel()
        getFilesJob.cancel()
        getFilesJob.cancelChildren()
    }

    fun enqueueBulkDownloadWorker(folderId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (FileController.hasFilesMarkedAsOffline(folderId)) {
                Utils.enqueueBulkDownloadWorker(context, folderId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { realm?.close() }
        cancelDownloadFiles()
    }

    fun shouldDisplaySubtitle(folderId: Int, userDrive: UserDrive?): Boolean {
        val folder = FileController.getFileById(folderId, userDrive) ?: return false
        return folder.getVisibilityType() == File.VisibilityType.IS_TEAM_SPACE
    }

    private companion object {
        const val TAG = "FileListViewModel"
        val mutex = Mutex()
    }
}
