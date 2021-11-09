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

import android.graphics.drawable.Drawable
import androidx.lifecycle.*
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.ui.fileList.FileListFragment.FolderFilesResult
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.SingleLiveEvent
import com.infomaniak.lib.core.models.ApiResponse
import io.realm.OrderedRealmCollection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FileListViewModel : ViewModel() {

    private var getFilesJob: Job = Job()
    private var getFolderActivitiesJob: Job = Job()

    lateinit var sortType: File.SortType

    val searchFileByName = MutableLiveData<String>()
    val searchResults = Transformations.switchMap(searchFileByName) { input ->
        searchFiles(input, sortType, currentPage)
    }

    var currentConvertedType: String? = null
    var currentConvertedTypeDrawable: Drawable? = null
    var currentConvertedTypeText: String? = null

    var isSharedWithMe = false

    var currentPage = 1
    var oldList: OrderedRealmCollection<File>? = null
    val isListMode = SingleLiveEvent<Boolean>()

    var lastItemCount: FileCount? = null

    fun sortTypeIsInitialized() = ::sortType.isInitialized

    fun getFiles(
        parentId: Int,
        page: Int = 1,
        ignoreCache: Boolean,
        order: File.SortType,
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
                        if (page == 1) {
                            emit(FolderFilesResult(resultList.first, resultList.second, true, page))
                        }
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

    fun getFavoriteFiles(order: File.SortType): LiveData<FolderFilesResult?> {
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

    private fun searchFiles(query: String, order: File.SortType, page: Int): LiveData<ApiResponse<ArrayList<File>>> {
        getFilesJob.cancel()
        getFilesJob = Job()

        return liveData(Dispatchers.IO + getFilesJob) {
            val type = currentConvertedType
            val apiResponse =
                ApiRepository.searchFiles(AccountUtils.currentDriveId, query, order.order, order.orderBy, page, type)

            when {
                apiResponse.isSuccess() -> emit(apiResponse)
                page == 1 -> emit(ApiResponse(ApiResponse.Status.SUCCESS, FileController.searchFiles(query, order)))
                else -> emit(apiResponse)
            }
        }
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

    fun getMySharedFiles(sortType: File.SortType): LiveData<Pair<ArrayList<File>, Boolean>?> {
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
    fun getFolderActivities(folder: File, userDrive: UserDrive? = null): LiveData<Map<out Int, File.LocalFileActivity>> {
        getFolderActivitiesJob.cancel()
        getFolderActivitiesJob = Job()
        return liveData(Dispatchers.IO + getFolderActivitiesJob) {
            mutex.withLock {
                val activities = FileController.getFolderActivities(folder, 1, userDrive)
                if (activities.isNotEmpty()) emit(activities)
            }
        }
    }

    fun cancelDownloadFiles() {
        getFilesJob.cancel()
        getFilesJob.cancelChildren()
    }

    override fun onCleared() {
        super.onCleared()
        cancelDownloadFiles()
    }

    companion object {
        private val mutex = Mutex()
    }
}
