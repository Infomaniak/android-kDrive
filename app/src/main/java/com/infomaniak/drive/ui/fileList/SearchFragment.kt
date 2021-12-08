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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFiltersBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFiltersViewModel
import com.infomaniak.drive.ui.fileList.FileListViewModel.*
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.utils.setPagination
import io.realm.RealmList
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.item_search_view.*
import kotlinx.android.synthetic.main.recent_searches.*
import java.util.*
import kotlin.collections.LinkedHashMap

class SearchFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false

    private lateinit var searchFiltersAdapter: SearchFiltersAdapter
    private lateinit var previousSearchesAdapter: PreviousSearchesAdapter
    private lateinit var recentSearchesView: View
    private var isDownloading = false

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        searchFiltersAdapter = SearchFiltersAdapter { key, categoryId -> removeFilter(key, categoryId) }
        filtersLayout.adapter = searchFiltersAdapter

        fileListViewModel.sortType = File.SortType.RECENT

        // Get preview List if needed
        if (mainViewModel.currentPreviewFileList.isNotEmpty()) {
            fileListViewModel.searchOldFileList = RealmList(*mainViewModel.currentPreviewFileList.values.toTypedArray())
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }

        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        recentSearchesView = layoutInflater.inflate(R.layout.recent_searches, null)
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = getString(R.string.searchTitle)
        searchViewCard.isVisible = true
        fileListLayout.addView(recentSearchesView, 1)

        clearButton.setOnClickListener { searchView.text = null }

        searchView.hint = getString(R.string.searchViewHint)
        searchView.addTextChangedListener(DebouncingTextWatcher(lifecycle) {
            clearButton?.isInvisible = it.isNullOrEmpty()
            fileListViewModel.currentPage = 1
            downloadFiles(true, false)
        })
        searchView.setOnEditorActionListener { _, actionId, _ ->
            if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                fileListViewModel.currentPage = 1
                downloadFiles(true, false)
                true
            } else false
        }

        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete && !isDownloading) {
                fileAdapter.showLoading()
                fileListViewModel.currentPage++
                downloadFiles(true, false)
            }
        })

        fileAdapter.apply {
            onEmptyList = { changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = false) }

            onFileClicked = { file ->
                if (file.isFolder()) {
                    fileListViewModel.cancelDownloadFiles()
                    safeNavigate(
                        SearchFragmentDirections.actionSearchFragmentToFileListFragment(file.id, file.name)
                    )
                } else {
                    val fileList = getFileObjectsList(null)
                    Utils.displayFile(mainViewModel, findNavController(), file, fileList)
                }
            }
        }

        if (fileAdapter.fileList.isEmpty() && searchFiltersAdapter.filters.isEmpty()) {
            showRecentSearchesLayout(true)
        }

        setSearchesAdapter()
        setToolbar()
        observeSearchResult()
        setBackActionHandlers()
    }

    private fun setSearchesAdapter() {
        previousSearchesAdapter = PreviousSearchesAdapter { searchView.setText(it) }
        recentSearchesList.adapter = previousSearchesAdapter
    }

    private fun setToolbar() = with(toolbar) {
        setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.selectFilters) {
                with(fileListViewModel) {
                    safeNavigate(
                        SearchFragmentDirections.actionSearchFragmentToSearchFiltersBottomSheetDialog(
                            date = dateFilter.second?.time ?: -1L,
                            type = typeFilter.second?.name,
                            categories = categoriesFilter.second?.map { it.id }?.toIntArray(),
                            categoriesOwnership = categoriesOwnershipFilter.second,
                        )
                    )
                }
            }
            true
        }

        toolbar.menu.findItem(R.id.selectFilters).isVisible = true
    }

    private fun observeSearchResult() {
        fileListViewModel.searchResults.observe(viewLifecycleOwner) {

            if (!swipeRefreshLayout.isRefreshing) return@observe

            it?.let { apiResponse ->

                if (apiResponse.isSuccess()) {

                    updateMostRecentSearches()

                    val searchList = apiResponse.data ?: arrayListOf()
                    searchList.apply { map { file -> file.isFromSearch = true } }

                    when {
                        fileListViewModel.currentPage == 1 -> {
                            fileAdapter.setFiles(searchList)
                            changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, false)
                            fileRecyclerView.scrollTo(0, 0)
                        }
                        searchList.isEmpty() || searchList.size < ApiRepository.PER_PAGE -> {
                            fileAdapter.addFileList(searchList)
                            fileAdapter.isComplete = true
                        }
                        else -> {
                            fileAdapter.addFileList(searchList)
                        }
                    }

                } else {
                    changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, false)
                    requireActivity().showSnackbar(apiResponse.translateError())
                }

            } ?: let {
                fileAdapter.isComplete = true
                changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, false)
            }

            isDownloading = false
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Bundle>(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_NAV_KEY) { bundle ->
            with(bundle) {
                setDateFilter(getLong(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_DATE_BUNDLE_KEY))
                setTypeFilter(getString(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_TYPE_BUNDLE_KEY))
                setCategoriesFilter(getIntArray(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY))
                setCategoriesOwnershipFilter(getInt(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY))
            }
            updateFilters()
        }
    }

    private fun setDateFilter(time: Long) {
        fileListViewModel.dateFilter = Pair(FilterKey.DATE, if (time != -1L) Date(time) else null)
    }

    private fun setTypeFilter(typeName: String?) {
        fileListViewModel.typeFilter = Pair(FilterKey.TYPE, typeName?.let { File.ConvertedType.valueOf(it) })
    }

    private fun setCategoriesFilter(categories: IntArray?) {
        fileListViewModel.categoriesFilter = Pair(
            FilterKey.CATEGORIES_FILTER,
            categories?.let { DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray()) }
        )
    }

    private fun setCategoriesOwnershipFilter(categoriesOwnership: Int) {
        fileListViewModel.categoriesOwnershipFilter = Pair(FilterKey.CATEGORIES_OWNERSHIP_FILTER, categoriesOwnership)
    }

    private fun updateMostRecentSearches() {
        val newSearch = searchView.text.toString()
        if (newSearch.isBlank()) return
        val previousSearches = AppSettings.mostRecentSearches
        val newSearches = previousSearches
            .apply {
                if (contains(newSearch)) {
                    move(previousSearches.indexOf(newSearch), 0)
                } else {
                    add(0, newSearch)
                }
            }
            .filterIndexed { index, _ -> index < MAX_MOST_RECENT_SEARCHES }
        AppSettings.mostRecentSearches = RealmList(*newSearches.toTypedArray())
        previousSearchesAdapter.setAll(newSearches)
    }

    private fun updateFilters() {
        with(fileListViewModel) {
            val filters = mutableListOf<SearchFilter>().apply {
                dateFilter.second?.let {
                    add(SearchFilter(key = dateFilter.first, text = it.toString(), icon = R.drawable.ic_calendar))
                }
                typeFilter.second?.let {
                    add(SearchFilter(key = typeFilter.first, text = getString(it.searchFilterName), icon = it.icon))
                }
                categoriesFilter.second?.forEach {
                    add(SearchFilter(categoriesFilter.first, it.getName(requireContext()), tint = it.color, categoryId = it.id))
                }
            }
            searchFiltersAdapter.setAll(filters)
            filtersLayout.isVisible = filters.isNotEmpty()
            if (searchView.text.isNotEmpty()) {
                currentPage = 1
                downloadFiles(true, false)
            }
        }
    }

    private fun removeFilter(filter: FilterKey, categoryId: Int?) {
        with(fileListViewModel) {
            when (filter) {
                FilterKey.DATE -> {
                    dateFilter = Pair(FilterKey.DATE, null)
                }
                FilterKey.TYPE -> {
                    typeFilter = Pair(FilterKey.TYPE, null)
                }
                FilterKey.CATEGORIES_FILTER -> {
                    if (categoryId != null) {
                        categoriesFilter.second?.let { categories ->
                            val cats = categories.filter { it.id != categoryId }
                            categoriesFilter = Pair(FilterKey.CATEGORIES_FILTER, if (cats.isEmpty()) null else cats)
                        }
                    }
                }
                FilterKey.CATEGORIES_OWNERSHIP_FILTER -> {
                    categoriesOwnershipFilter = Pair(
                        FilterKey.CATEGORIES_OWNERSHIP_FILTER,
                        SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_FILTER_VALUE,
                    )
                }
            }
        }

        updateFilters()
    }

    override fun onPause() {
        fileListViewModel.searchOldFileList = fileAdapter.getFiles()
        searchView.isFocusable = false
        super.onPause()
    }

    private fun showRecentSearchesLayout(show: Boolean) {
        if (show) {
            changeNoFilesLayoutVisibility(hideFileList = false, changeControlsVisibility = false)
            filtersLayout.isGone = true
            fileRecyclerView.isGone = true
            recentSearchesView.isVisible = true
            sortLayout.isGone = true
        } else {
            fileRecyclerView.isVisible = true
            filtersLayout.isVisible = true
            recentSearchesView.isGone = true
            sortLayout.isVisible = true
        }
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_search_grey,
                title = R.string.searchNoFile,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            swipeRefreshLayout.isRefreshing = true
            val currentQuery = searchView?.text?.toString()

            if (currentQuery.isNullOrEmpty() && searchFiltersAdapter.filters.isEmpty()) {
                fileAdapter.setFiles(arrayListOf())
                showRecentSearchesLayout(true)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = fileListViewModel.searchOldFileList?.toMutableList() as? ArrayList
            if (!oldList.isNullOrEmpty() && fileAdapter.getFiles().isEmpty()) {
                fileAdapter.setFiles(oldList)
                fileListViewModel.searchOldFileList = null
                if (searchFiltersAdapter.filters.isNotEmpty()) filtersLayout.isVisible = true
                showRecentSearchesLayout(false)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            isDownloading = true
            showRecentSearchesLayout(false)
            fileListViewModel.searchFileByName.value = currentQuery
        }
    }

    private companion object {
        const val MAX_MOST_RECENT_SEARCHES = 5
    }
}
