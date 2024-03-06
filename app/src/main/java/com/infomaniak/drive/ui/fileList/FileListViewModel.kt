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
package com.infomaniak.drive.ui.fileList

import android.app.Application
import androidx.lifecycle.*
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.Type
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FileId
import com.infomaniak.drive.utils.Position
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FileListViewModel(application: Application) : AndroidViewModel(application) {

    private inline val context get() = getApplication<MainApplication>().applicationContext

    private var getFilesJob: Job = Job()
    private var getFolderActivitiesJob: Job = Job()
    private var checkOfflineFilesJob = Job()

    lateinit var sortType: SortType

    var isSharedWithMe = false

    val isListMode = SingleLiveEvent<Boolean>()

    var lastItemCount: FileCount? = null

    fun sortTypeIsInitialized() = ::sortType.isInitialized

    fun getFiles(
        parentId: Int,
        page: Int = 1,
        ignoreCache: Boolean,
        order: SortType,
        ignoreCloud: Boolean = false,
        userDrive: UserDrive? = null
    ): LiveData<FolderFilesResult?> {
        getFilesJob.cancel()
        getFolderActivitiesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            tailrec suspend fun recursiveDownload(parentId: Int, page: Int) {
                getFilesJob.ensureActive()
                val resultList = FileController.getFilesFromCacheOrDownload(
                    parentId = parentId,
                    page = page,
                    ignoreCache = ignoreCache,
                    ignoreCloud = ignoreCloud,
                    order = order,
                    userDrive = userDrive,
                    withChildren = true
                )

                when {
                    resultList == null -> emit(null)
                    resultList.second.size < ApiRepository.PER_PAGE -> {
                        emit(FolderFilesResult(resultList.first, resultList.second, true, page))
                    }
                    else -> {
                        if (page == 1) {
                            emit(FolderFilesResult(resultList.first, resultList.second, true, page))
                        }
                        recursiveDownload(parentId, page + 1)
                    }
                }
            }
            recursiveDownload(parentId, page)
        }
    }

    fun getFavoriteFiles(order: SortType): LiveData<FolderFilesResult?> {
        getFilesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            suspend fun recursive(page: Int) {
                getFilesJob.ensureActive()
                val apiResponse = ApiRepository.getFavoriteFiles(AccountUtils.currentDriveId, order, page)
                if (apiResponse.isSuccess()) {
                    when {
                        apiResponse.data.isNullOrEmpty() -> emit(null)
                        apiResponse.data!!.size < ApiRepository.PER_PAGE -> {
                            FileController.saveFavoritesFiles(apiResponse.data!!, page == 1)
                            emit(FolderFilesResult(files = apiResponse.data!!, isComplete = true, page = apiResponse.page))
                        }
                        else -> {
                            apiResponse.data?.let { FileController.saveFavoritesFiles(it, page == 1) }
                            emit(FolderFilesResult(files = apiResponse.data!!, isComplete = false, page = apiResponse.page))
                            recursive(page + 1)
                        }
                    }
                } else emit(
                    FolderFilesResult(
                        files = FileController.getFilesFromCache(FileController.FAVORITES_FILE_ID),
                        isComplete = true,
                        page = 1
                    )
                )
            }
            recursive(1)
        }
    }

    fun getPendingFilesCount(folderId: Int) = liveData(Dispatchers.IO) {
        emit(UploadFile.getCurrentUserPendingUploadsCount(folderId))
    }

    fun getFileCount(folder: File): LiveData<FileCount> = liveData(Dispatchers.IO) {
        lastItemCount?.let { emit(it) }
        ApiRepository.getFileCount(folder).data?.let { fileCount ->
            lastItemCount = fileCount
            emit(fileCount)
        }
    }

    fun getMySharedFiles(sortType: SortType): LiveData<Pair<ArrayList<File>, Boolean>?> {
        getFilesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            FileController.getMySharedFiles(UserDrive(), sortType) { files, isComplete ->
                runBlocking { emit(files to isComplete) }
            }
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
                getFolderActivitiesJob.ensureActive()
                val activities = FileController.getFolderActivities(folder, 1, userDrive)
                emit(activities.isNotEmpty())
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
            with(notification) { importId?.let { FileController.updateExternalImport(driveId, it, action) } }
        }
    }

    fun cancelDownloadFiles() {
        pendingJob.cancel()
        getFilesJob.cancel()
        getFilesJob.cancelChildren()
    }

    override fun onCleared() {
        super.onCleared()
        cancelDownloadFiles()
    }

    private companion object {
        val mutex = Mutex()
    }
}
