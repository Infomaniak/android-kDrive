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
package com.infomaniak.drive.ui.fileList

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.SearchCategoriesOwnershipFilter
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.fileList.SearchFragment.VisibilityMode
import com.infomaniak.drive.utils.AccountUtils
import io.realm.OrderedRealmCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import java.util.Date

class SearchViewModel : ViewModel() {

    private var searchFilesJob: Job = Job()

    val visibilityMode = MutableLiveData(VisibilityMode.RECENT_SEARCHES)

    private var currentCursor: String? = null
    val searchFileByName = MutableLiveData<Pair<String, SortType>>()
    val searchResults = searchFileByName.switchMap { (query, sortType) ->
        searchFiles(query, sortType)
    }

    // Adding a TextChangedListener on an EditText makes it trigger immediately,
    // so we need this boolean to know if we really want to trigger it, or not.
    var previousSearch: String? = null

    var dateFilter: SearchDateFilter? = null
    var typeFilter: ExtensionType? = null
    var categoriesFilter: List<Category>? = null
    var categoriesOwnershipFilter: SearchCategoriesOwnershipFilter? = null

    var searchOldFileList: OrderedRealmCollection<File>? = null

    fun resetSearchPagination() {
        currentCursor = null
    }

    private fun searchFiles(query: String, order: SortType): LiveData<FileListFragment.FolderFilesResult> {
        searchFilesJob.cancel()
        searchFilesJob = Job()
        return liveData(Dispatchers.IO + searchFilesJob) {
            val apiResponse = ApiRepository.searchFiles(
                driveId = AccountUtils.currentDriveId,
                query = query,
                sortType = order,
                cursor = currentCursor,
                date = formatDate(),
                type = typeFilter?.value,
                categories = formatCategories(),
            )

            if (apiResponse.isSuccess() || currentCursor != null) {
                emit(
                    FileListFragment.FolderFilesResult(
                        files = apiResponse.data ?: arrayListOf(),
                        isComplete = !apiResponse.hasMore,
                        isFirstPage = currentCursor == null,
                        errorRes = if (apiResponse.isSuccess()) null else apiResponse.translateError(),
                        isNewSort = false,
                    )
                )
            } else {
                emit(
                    FileListFragment.FolderFilesResult(
                        files = FileController.searchFiles(query, order),
                        isComplete = true,
                        isFirstPage = true,
                        errorRes = null,
                        isNewSort = false,
                    )
                )
            }

            currentCursor = apiResponse.cursor
        }
    }

    private fun formatDate(): Pair<String, String>? {
        fun Date.timestamp(): String = (time / 1_000L).toString()
        return dateFilter?.let { it.start.timestamp() to it.end.timestamp() }
    }

    private fun formatCategories(): String? {
        return categoriesFilter?.joinToString(
            separator = categoriesOwnershipFilter?.apiSeparator ?: return null
        ) { it.id.toString() }
    }

    fun cancelDownloadFiles() {
        searchFilesJob.cancel()
        searchFilesJob.cancelChildren()
    }

    override fun onCleared() {
        super.onCleared()
        cancelDownloadFiles()
    }
}
