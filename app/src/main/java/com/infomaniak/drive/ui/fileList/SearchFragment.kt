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
import android.widget.EditText
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.*
import com.infomaniak.drive.data.models.SearchFilter.*
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

    private val searchViewModel: SearchViewModel by navGraphViewModels(R.id.searchFragment)

    override var enabledMultiSelectMode: Boolean = false

    private lateinit var filtersAdapter: SearchFiltersAdapter
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter
    private lateinit var recentSearchesView: View
    private var isDownloading = false

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configureViewModels()
        setFiltersAdapter()

        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        recentSearchesView = layoutInflater.inflate(R.layout.recent_searches, null)

        super.onViewCreated(view, savedInstanceState)

        clearButton.setOnClickListener { searchView.setText("") }
        configureSearchView()
        configureFileAdapter()
        configureRecentSearches()
        configureToolbar()
        observeSearchResults()
        updateFilters()
    }

    override fun onPause() {
        searchViewModel.searchOldFileList = fileAdapter.getFiles()
        searchView.isFocusable = false
        super.onPause()
    }

    override fun onStop() {
        UISettings(requireContext()).recentSearches = recentSearchesAdapter.searches
        super.onStop()
    }

    private fun configureViewModels() {
        fileListViewModel.sortType = SortType.RECENT

        // Get preview List if needed
        if (mainViewModel.currentPreviewFileList.isNotEmpty()) {
            searchViewModel.searchOldFileList = RealmList(*mainViewModel.currentPreviewFileList.values.toTypedArray())
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }
    }

    private fun setFiltersAdapter() {
        filtersAdapter = SearchFiltersAdapter(
            onFilterRemoved = { key, categoryId -> removeFilter(key, categoryId) }
        ).also {
            filtersRecyclerView.adapter = it
        }
    }

    private fun configureSearchView() {
        searchViewCard.isVisible = true
        with(searchView) {
            hint = getString(R.string.searchViewHint)
            addTextChangedListener()
            setOnEditorActionListener()
        }
    }

    private fun EditText.addTextChangedListener() {
        addTextChangedListener(DebouncingTextWatcher(lifecycle) {
            clearButton?.isInvisible = it.isNullOrEmpty()
            searchViewModel.currentPage = 1
            downloadFiles(true, false)
        })
    }

    private fun EditText.setOnEditorActionListener() {
        setOnEditorActionListener { _, actionId, _ ->
            if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                searchViewModel.currentPage = 1
                downloadFiles(true, false)
                true
            } else false
        }
    }

    private fun configureFileAdapter() {
        setFileRecyclerPagination()
        setFileAdapterListener()
        if (fileAdapter.fileList.isEmpty() && filtersAdapter.filters.isEmpty()) changeRecentSearchesLayoutVisibility(true)
    }

    private fun setFileRecyclerPagination() {
        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete && !isDownloading) {
                fileAdapter.showLoading()
                searchViewModel.currentPage++
                downloadFiles(true, false)
            }
        })
    }

    private fun setFileAdapterListener() {
        fileAdapter.apply {
            onEmptyList = { changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = false) }

            onFileClicked = { file ->
                if (file.isFolder()) {
                    searchViewModel.cancelDownloadFiles()
                    safeNavigate(
                        SearchFragmentDirections.actionSearchFragmentToFileListFragment(
                            folderID = file.id,
                            folderName = file.name,
                            shouldHideBottomNavigation = true,
                        )
                    )
                } else {
                    val fileList = getFileObjectsList(null)
                    Utils.displayFile(mainViewModel, findNavController(), file, fileList)
                }
            }
        }
    }

    private fun configureRecentSearches() {
        fileListLayout.addView(recentSearchesView, 1)

        val recentSearches = UISettings(requireContext()).recentSearches
        recentSearchesContainer.isGone = recentSearches.isEmpty()

        recentSearchesAdapter = RecentSearchesAdapter(
            searches = ArrayList(recentSearches),
            onSearchClicked = searchView::setText,
        ).apply {
            recentSearchesRecyclerView.adapter = this
        }
    }

    private fun configureToolbar() {
        collapsingToolbarLayout.title = getString(R.string.searchTitle)
        setToolbarListener()
        toolbar.menu.findItem(R.id.selectFilters).isVisible = true
    }

    private fun setToolbarListener() {
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.selectFilters) {
                safeNavigate(R.id.searchFiltersFragment)
                true
            } else {
                false
            }
        }
    }

    private fun observeSearchResults() {
        searchViewModel.searchResults.observe(viewLifecycleOwner) {

            if (!swipeRefreshLayout.isRefreshing) return@observe

            it?.let { apiResponse ->

                if (apiResponse.isSuccess()) {

                    updateMostRecentSearches()

                    val searchList = (apiResponse.data ?: arrayListOf()).apply {
                        map { file -> file.isFromSearch = true }
                    }

                    when {
                        searchViewModel.currentPage == 1 -> {
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

    private fun updateMostRecentSearches() {
        val newSearch = searchView.text.toString().trim()
        if (newSearch.isEmpty()) return

        val newSearches = (listOf(newSearch) + recentSearchesAdapter.searches).distinct()
            .take(MAX_MOST_RECENT_SEARCHES)
            .also(recentSearchesAdapter::setItems)

        recentSearchesContainer.isGone = newSearches.isEmpty()
    }

    private fun updateFilters(shouldUpdateAdapter: Boolean = true) {
        arrayListOf<SearchFilter>().apply {
            createDateFilter()?.let(::add)
            createTypeFilter()?.let(::add)
            createCategoriesFilter()?.let(::addAll)
        }.also {
            if (shouldUpdateAdapter) filtersAdapter.setItems(it)
            changeRecentSearchesLayoutVisibility(it.isEmpty() && searchView.text.toString().isBlank())
        }
        searchViewModel.currentPage = 1
        downloadFiles(true, false)
    }

    private fun createDateFilter(): SearchFilter? {
        return searchViewModel.dateFilter?.let {
            SearchFilter(key = FilterKey.DATE, text = it.text, icon = R.drawable.ic_calendar)
        }
    }

    private fun createTypeFilter(): SearchFilter? {
        return searchViewModel.typeFilter?.let {
            SearchFilter(key = FilterKey.TYPE, text = getString(it.searchFilterName), icon = it.icon)
        }
    }

    private fun createCategoriesFilter(): List<SearchFilter>? {
        return searchViewModel.categoriesFilter?.map {
            SearchFilter(key = FilterKey.CATEGORIES, text = it.getName(requireContext()), tint = it.color, categoryId = it.id)
        }
    }

    private fun removeFilter(filter: FilterKey, categoryId: Int?) {
        when (filter) {
            FilterKey.DATE -> removeDateFilter()
            FilterKey.TYPE -> removeTypeFilter()
            FilterKey.CATEGORIES -> removeCategoryFilter(categoryId)
            FilterKey.CATEGORIES_OWNERSHIP -> Unit // It's impossible to remove this filter by clicking on it
        }
        updateFilters(shouldUpdateAdapter = false)
    }

    private fun removeDateFilter() {
        searchViewModel.dateFilter = null
    }

    private fun removeTypeFilter() {
        searchViewModel.typeFilter = null
    }

    private fun removeCategoryFilter(categoryId: Int?) = with(searchViewModel) {
        if (categoryId != null) {
            categoriesFilter?.let { categories ->
                val filteredCategories = categories.filter { it.id != categoryId }
                categoriesFilter = if (filteredCategories.isEmpty()) null else filteredCategories
            }
        }
    }

    private fun changeRecentSearchesLayoutVisibility(shouldDisplay: Boolean) {
        if (shouldDisplay) {
            filtersRecyclerView.isGone = true
            sortLayout.isGone = true
            fileRecyclerView.isGone = true
            noFilesLayout.isGone = true
            recentSearchesView.isVisible = true
        } else {
            if (filtersAdapter.filters.isNotEmpty()) filtersRecyclerView.isVisible = true
            val shouldDisplayNoFilesLayout = !swipeRefreshLayout.isRefreshing
            if (shouldDisplayNoFilesLayout) {
                changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = false)
            } else {
                noFilesLayout.isGone = true
            }
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

            val currentQuery = searchView?.text?.toString()?.trim() ?: ""

            if (currentQuery.isEmpty() && filtersAdapter.filters.isEmpty()) {
                fileAdapter.setFiles(arrayListOf())
                changeRecentSearchesLayoutVisibility(true)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = searchViewModel.searchOldFileList?.toMutableList() as? ArrayList
            if (oldList?.isNotEmpty() == true && fileAdapter.getFiles().isEmpty()) {
                fileAdapter.setFiles(oldList)
                searchViewModel.searchOldFileList = null
                changeRecentSearchesLayoutVisibility(false)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            swipeRefreshLayout.isRefreshing = true
            isDownloading = true
            changeRecentSearchesLayoutVisibility(false)
            searchViewModel.searchFileByName.value = currentQuery to fileListViewModel.sortType
        }
    }

    private companion object {
        const val MAX_MOST_RECENT_SEARCHES = 5
    }
}
