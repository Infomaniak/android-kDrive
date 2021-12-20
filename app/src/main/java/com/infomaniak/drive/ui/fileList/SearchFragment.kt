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
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.*
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchDateFilter
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
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter
    private lateinit var recentSearchesView: View
    private var isDownloading = false

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        searchFiltersAdapter = SearchFiltersAdapter { key, categoryId -> removeFilter(key, categoryId) }
        filtersLayout.adapter = searchFiltersAdapter

        fileListViewModel.sortType = SortType.RECENT

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

        setRecentSearchesAdapter()
        setToolbar()
        observeSearchResult()
        setBackActionHandlers()
    }

    private fun setRecentSearchesAdapter() {
        recentSearchesAdapter = RecentSearchesAdapter { searchView.setText(it) }.apply {
            setAll(AppSettings.mostRecentSearches)
        }
        recentSearchesList.adapter = recentSearchesAdapter
    }

    private fun setToolbar() = with(toolbar) {
        setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.selectFilters) {
                with(fileListViewModel) {
                    safeNavigate(
                        SearchFragmentDirections.actionSearchFragmentToSearchFiltersBottomSheetDialog(
                            date = dateFilter.second,
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

    override fun onResume() {
        super.onResume()
        updateFilters()
    }

    private fun observeSearchResult() {
        fileListViewModel.searchResults.observe(viewLifecycleOwner) {

            if (!swipeRefreshLayout.isRefreshing) return@observe

            it?.let { apiResponse ->

                if (apiResponse.isSuccess()) {

                    updateMostRecentSearches()

                    val searchList = (apiResponse.data ?: arrayListOf())
                        .apply { map { file -> file.isFromSearch = true } }

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
                setDateFilter(getParcelable(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_DATE_BUNDLE_KEY))
                setTypeFilter(getParcelable(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_TYPE_BUNDLE_KEY))
                setCategoriesFilter(getIntArray(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY))
                setCategoriesOwnershipFilter(getParcelable(SearchFiltersBottomSheetDialog.SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY))
            }
            updateFilters()
        }
    }

    private fun setDateFilter(filter: SearchDateFilter?) {
        fileListViewModel.dateFilter = Pair(FilterKey.DATE, filter)
    }

    private fun setTypeFilter(type: ConvertedType?) {
        fileListViewModel.typeFilter = Pair(FilterKey.TYPE, type)
    }

    private fun setCategoriesFilter(categories: IntArray?) {
        fileListViewModel.categoriesFilter = Pair(
            FilterKey.CATEGORIES,
            categories?.let { DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray()) })
    }

    private fun setCategoriesOwnershipFilter(categoriesOwnership: CategoriesOwnershipFilter?) {
        categoriesOwnership?.let { fileListViewModel.categoriesOwnershipFilter = Pair(FilterKey.CATEGORIES_OWNERSHIP, it) }
    }

    private fun updateMostRecentSearches() {
        val newSearch = searchView.text.toString().trim()
        if (newSearch.isEmpty()) return

        val recentSearches = AppSettings.mostRecentSearches
        val newSearches = recentSearches
            .apply {
                if (contains(newSearch)) {
                    move(recentSearches.indexOf(newSearch), 0)
                } else {
                    add(0, newSearch)
                }
            }
            .filterIndexed { index, _ -> index < MAX_MOST_RECENT_SEARCHES }
        AppSettings.mostRecentSearches = RealmList(*newSearches.toTypedArray())
        recentSearchesAdapter.setAll(newSearches)
    }

    private fun updateFilters() {
        with(fileListViewModel) {
            val filters = mutableListOf<SearchFilter>().apply {
                dateFilter.second?.let {
                    add(SearchFilter(key = dateFilter.first, text = it.text, icon = R.drawable.ic_calendar))
                }
                typeFilter.second?.let {
                    add(SearchFilter(key = typeFilter.first, text = getString(it.searchFilterName), icon = it.icon))
                }
                categoriesFilter.second?.forEach {
                    add(SearchFilter(categoriesFilter.first, it.getName(requireContext()), tint = it.color, categoryId = it.id))
                }
            }
            searchFiltersAdapter.setAll(filters)
            showRecentSearchesLayout(filters.isEmpty() && searchView.text.toString().isBlank())
            currentPage = 1
            downloadFiles(true, false)
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
                FilterKey.CATEGORIES -> {
                    if (categoryId != null) {
                        categoriesFilter.second?.let { categories ->
                            val cats = categories.filter { it.id != categoryId }
                            categoriesFilter = Pair(FilterKey.CATEGORIES, if (cats.isEmpty()) null else cats)
                        }
                    }
                }
                FilterKey.CATEGORIES_OWNERSHIP -> {
                    categoriesOwnershipFilter = Pair(
                        FilterKey.CATEGORIES_OWNERSHIP,
                        SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE,
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

    private fun showRecentSearchesLayout(isShown: Boolean) {
        if (isShown) {
            filtersLayout.isGone = true
            sortLayout.isGone = true
            fileRecyclerView.isGone = true
            noFilesLayout.isGone = true
            recentSearchesView.isVisible = true
        } else {
            if (searchFiltersAdapter.filters.isNotEmpty()) filtersLayout.isVisible = true
            changeNoFilesLayoutVisibility(!swipeRefreshLayout.isRefreshing, false)
            recentSearchesView.isGone = true
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

            val currentQuery = searchView?.text?.toString()?.trim()

            if (currentQuery.isNullOrEmpty() && searchFiltersAdapter.filters.isEmpty()) {
                fileAdapter.setFiles(arrayListOf())
                showRecentSearchesLayout(true)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = fileListViewModel.searchOldFileList?.toMutableList() as? ArrayList
            if (oldList?.isNotEmpty() == true && fileAdapter.getFiles().isEmpty()) {
                fileAdapter.setFiles(oldList)
                fileListViewModel.searchOldFileList = null
                showRecentSearchesLayout(false)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            swipeRefreshLayout.isRefreshing = true
            isDownloading = true
            showRecentSearchesLayout(false)
            fileListViewModel.searchFileByName.value = currentQuery
        }
    }

    private companion object {
        const val MAX_MOST_RECENT_SEARCHES = 5
    }
}
