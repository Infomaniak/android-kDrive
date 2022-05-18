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

import androidx.lifecycle.*
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
import com.infomaniak.lib.core.models.ApiResponse
import io.realm.OrderedRealmCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import java.util.*

class SearchViewModel : ViewModel() {

    private var searchFilesJob: Job = Job()

    val visibilityMode = MutableLiveData(VisibilityMode.RECENT_SEARCHES)

    val searchFileByName = MutableLiveData<Pair<String, SortType>>()
    val searchResults = Transformations.switchMap(searchFileByName) { (query, sortType) ->
        searchFiles(query, sortType, currentPage)
    }

    // Adding a TextChangedListener on an EditText makes it trigger immediately,
    // so we need this boolean to know if we really want to trigger it, or not.
    var previousSearch: String? = null

    var dateFilter: SearchDateFilter? = null
    var typeFilter: ExtensionType? = null
    var categoriesFilter: List<Category>? = null
    var categoriesOwnershipFilter: SearchCategoriesOwnershipFilter? = null

    var currentPage = 1
    var searchOldFileList: OrderedRealmCollection<File>? = null

    private fun searchFiles(query: String, order: SortType, page: Int): LiveData<ApiResponse<ArrayList<File>>> {
        searchFilesJob.cancel()
        searchFilesJob = Job()
        return liveData(Dispatchers.IO + searchFilesJob) {
            val apiResponse = ApiRepository.searchFiles(
                driveId = AccountUtils.currentDriveId,
                query = query,
                sortType = order,
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
        return dateFilter?.let { it.start.timestamp() to it.end.timestamp() }
    }

    private fun formatType() = typeFilter?.value?.lowercase()

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
