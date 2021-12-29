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
package com.infomaniak.drive.ui.fileList

import androidx.lifecycle.*
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.*
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FileId
import com.infomaniak.drive.utils.Position
import com.infomaniak.drive.utils.SingleLiveEvent
import com.infomaniak.lib.core.models.ApiResponse
import io.realm.OrderedRealmCollection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class FileListViewModel : ViewModel() {

    private var getFilesJob: Job = Job()
    private var getFolderActivitiesJob: Job = Job()

    lateinit var sortType: SortType

    val searchFileByName = MutableLiveData<String>()
    val searchResults = Transformations.switchMap(searchFileByName) { input ->
        searchFiles(input, sortType, currentPage)
    }

    var dateFilter: Pair<FilterKey, SearchDateFilter?> = FilterKey.DATE to null
    var typeFilter: Pair<FilterKey, ConvertedType?> = FilterKey.TYPE to null
    var categoriesFilter: Pair<FilterKey, List<Category>?> = FilterKey.CATEGORIES to null
    var categoriesOwnershipFilter: Pair<FilterKey, CategoriesOwnershipFilter> =
        FilterKey.CATEGORIES_OWNERSHIP to SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE

    var isSharedWithMe = false

    var currentPage = 1
    var searchOldFileList: OrderedRealmCollection<File>? = null
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
            suspend fun recursiveDownload(parentId: Int, page: Int) {
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

    private fun searchFiles(query: String, order: SortType, page: Int): LiveData<ApiResponse<ArrayList<File>>> {
        getFilesJob.cancel()
        getFilesJob = Job()
        return liveData(Dispatchers.IO + getFilesJob) {
            val apiResponse = ApiRepository.searchFiles(
                driveId = AccountUtils.currentDriveId,
                query = query,
                order = order.order,
                orderBy = order.orderBy,
                page = page,
                date = formatDate(),
                type = formatType(),
                categories = formatCategories(),
            )

            when {
                apiResponse.isSuccess() -> emit(apiResponse)
                page == 1 -> emit(ApiResponse(ApiResponse.Status.SUCCESS, FileController.searchFiles(query, order)))
                else -> emit(apiResponse)
            }
        }
    }

    private fun formatDate(): Pair<String, String>? {
        fun Date.timestamp(): String = (time / 1_000L).toString()
        return dateFilter.second?.let { it.start.timestamp() to it.end.timestamp() }
    }

    private fun formatType() = typeFilter.second?.name?.lowercase(Locale.ROOT)

    private fun formatCategories(): String? {
        return categoriesFilter.second?.joinToString(
            separator = if (categoriesOwnershipFilter.second == CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES) {
                SEPARATOR_BELONG_TO_ALL_CATEGORIES
            } else {
                SEPARATOR_BELONG_TO_ONE_CATEGORY
            }
        ) { it.id.toString() }
    }

    fun getPendingFilesCount(folderID: Int) = liveData(Dispatchers.IO) {
        emit(UploadFile.getCurrentUserPendingUploadsCount(folderID))
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

    fun performCancellableBulkOperation(bulkOperation: BulkOperation): LiveData<ApiResponse<CancellableAction>> {
        return liveData(Dispatchers.IO) {
            emit(ApiRepository.performCancellableBulkOperation(bulkOperation))
        }
    }

    @Synchronized
    fun getFolderActivities(folder: File, userDrive: UserDrive? = null): LiveData<Map<out Int, LocalFileActivity>> {
        getFolderActivitiesJob.cancel()
        getFolderActivitiesJob = Job()
        return liveData(Dispatchers.IO + getFolderActivitiesJob) {
            mutex.withLock {
                getFolderActivitiesJob.ensureActive()
                val activities = FileController.getFolderActivities(folder, 1, userDrive)
                if (activities.isNotEmpty()) emit(activities)
            }
        }
    }

    private var pendingJob = Job()
    val currentAdapterPendingFiles = MutableLiveData<ArrayList<File>>()
    val indexUploadToDelete = Transformations.switchMap(currentAdapterPendingFiles) { files ->
        val adapterPendingFileIds = files.map { it.id }
        val isFileType = files.firstOrNull()?.type == Type.FILE.value
        pendingFilesToDelete(adapterPendingFileIds, isFileType)
    }

    private fun pendingFilesToDelete(adapterPendingFileIds: List<Int>, isFileType: Boolean):
            LiveData<ArrayList<Pair<Position, FileId>>> {

        pendingJob.cancel()
        pendingJob = Job()

        return liveData(Dispatchers.IO + pendingJob) {
            val uploadRealm = UploadFile.getRealmInstance()
            val positions = arrayListOf<Pair<Position, FileId>>()
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
            emit(positions)
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
        const val SEPARATOR_BELONG_TO_ALL_CATEGORIES = "%26"
        const val SEPARATOR_BELONG_TO_ONE_CATEGORY = "|"
    }
}
