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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.SearchFilter
import com.infomaniak.drive.data.models.SearchFilter.FilterKey
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getName
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.item_search_view.*
import kotlinx.android.synthetic.main.recent_searches.*

class SearchFragment : FileListFragment() {

    private val searchViewModel: SearchViewModel by navGraphViewModels(R.id.searchFragment)

    override var enabledMultiSelectMode: Boolean = false

    private lateinit var filtersAdapter: SearchFiltersAdapter
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter
    private lateinit var recentSearchesView: View
    private var isDownloading = false

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        recentSearchesView = layoutInflater.inflate(R.layout.recent_searches, null)

        configureFileListViewModel()
        configureFiltersAdapter()
        configureFilters()

        super.onViewCreated(view, savedInstanceState)

        observeVisibilityModeUpdates()
        configureClearButtonListener()
        configureSearchView()
        configureFileRecyclerPagination()
        configureFileAdapterListener()
        configureRecentSearches()
        configureToolbar()
        observeSearchResults()
    }

    override fun onResume() {
        super.onResume()
        updateClearButton(searchView.text.toString())
        triggerSearch()
    }

    override fun onPause() {
        searchViewModel.searchOldFileList = fileAdapter.getFiles()
        searchView.isFocusable = false
        super.onPause()
    }

    override fun onStop() {
        searchViewModel.previousSearch = searchView.text.toString()
        UiSettings(requireContext()).recentSearches = recentSearchesAdapter.searches
        super.onStop()
    }

    private fun configureFileListViewModel() {
        fileListViewModel.sortType = SortType.RECENT
    }

    private fun configureFiltersAdapter() {
        filtersAdapter = SearchFiltersAdapter(
            onFilterRemoved = { key, categoryId -> removeFilter(key, categoryId) }
        ).also {
            filtersRecyclerView.adapter = it
        }
    }

    private fun configureFilters() = with(searchViewModel) {
        filtersAdapter.setItems(
            arrayListOf<SearchFilter>().apply {
                uiDateFilter()?.let(::add)
                uiTypeFilter()?.let(::add)
                uiCategoriesFilter()?.let(::addAll)
            }
        )
    }

    private fun SearchViewModel.uiDateFilter(): SearchFilter? {
        return dateFilter?.let {
            SearchFilter(key = FilterKey.DATE, text = it.text, icon = R.drawable.ic_calendar)
        }
    }

    private fun SearchViewModel.uiTypeFilter(): SearchFilter? {
        return typeFilter?.let {
            SearchFilter(key = FilterKey.TYPE, text = getString(it.searchFilterName), icon = it.icon)
        }
    }

    private fun SearchViewModel.uiCategoriesFilter(): List<SearchFilter>? {
        return categoriesFilter?.map {
            SearchFilter(key = FilterKey.CATEGORIES, text = it.getName(requireContext()), tint = it.color, categoryId = it.id)
        }
    }

    private fun observeVisibilityModeUpdates() {
        searchViewModel.visibilityMode.observe(viewLifecycleOwner) { updateUi(it) }
    }

    private fun configureClearButtonListener() {
        clearButton.setOnClickListener { searchView.setText("") }
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
            if (searchViewModel.previousSearch != null) {
                searchViewModel.previousSearch = null
                return@DebouncingTextWatcher
            }
            updateClearButton(it)
            triggerSearch()
        })
    }

    private fun EditText.setOnEditorActionListener() {
        setOnEditorActionListener { _, actionId, _ ->
            (EditorInfo.IME_ACTION_SEARCH == actionId).also { if (it) triggerSearch() }
        }
    }

    private fun updateClearButton(text: String?) {
        clearButton.isInvisible = text.isNullOrEmpty()
    }

    private fun configureFileRecyclerPagination() {
        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete && !isDownloading) {
                fileAdapter.showLoading()
                searchViewModel.currentPage++
                downloadFiles(true, false)
            }
        })
    }

    private fun configureFileAdapterListener() {
        fileAdapter.onFileClicked = { file ->
            if (file.isFolder()) {
                searchViewModel.cancelDownloadFiles()
                safeNavigate(
                    SearchFragmentDirections.actionSearchFragmentToFileListFragment(
                        folderId = file.id,
                        folderName = file.name,
                        shouldHideBottomNavigation = true,
                    )
                )
            } else {
                val fileList = fileAdapter.getFileObjectsList(null)
                Utils.displayFile(mainViewModel, findNavController(), file, fileList)
            }
        }
    }

    private fun configureRecentSearches() {
        fileListLayout.addView(recentSearchesView, 1)

        val recentSearches = UiSettings(requireContext()).recentSearches

        recentSearchesAdapter = RecentSearchesAdapter(
            searches = ArrayList(recentSearches),
            onSearchClicked = searchView::setText,
        ).also {
            recentSearchesRecyclerView.adapter = it
            recentSearchesContainer.isGone = recentSearches.isEmpty()
        }
    }

    private fun configureToolbar() {
        collapsingToolbarLayout.title = getString(R.string.searchTitle)
        setToolbarListener()
        toolbar.menu.findItem(R.id.selectFilters).isVisible = true
    }

    private fun setToolbarListener() {
        toolbar.setOnMenuItemClickListener { menuItem ->
            (menuItem.itemId == R.id.selectFilters).also { if (it) safeNavigate(R.id.searchFiltersFragment) }
        }
    }

    private fun observeSearchResults() {

        fun handleLiveDataTriggerWhenInitialized() {
            fileAdapter.isComplete = true
        }

        fun handleApiCallFailure(apiResponse: ApiResponse<ArrayList<File>>) {
            searchViewModel.visibilityMode.value = VisibilityMode.NO_RESULTS
            showSnackbar(apiResponse.translateError())
        }

        fun getSearchResults(data: ArrayList<File>?): ArrayList<File> {
            return (data ?: arrayListOf()).apply {
                map { file -> file.isFromSearch = true }
            }
        }

        fun handleFirstResult(searchList: ArrayList<File>) {
            fileAdapter.setFiles(searchList)
            fileRecyclerView.scrollTo(0, 0)
            searchViewModel.visibilityMode.value = if (searchList.isEmpty()) VisibilityMode.NO_RESULTS else VisibilityMode.RESULTS
        }

        fun handleNoResult() {
            fileAdapter.apply {
                hideLoading()
                isComplete = true
            }
        }

        fun handleLastPage(searchList: ArrayList<File>) {
            fileAdapter.apply {
                addFileList(searchList)
                isComplete = true
            }
        }

        fun handleNewPage(searchList: ArrayList<File>) {
            fileAdapter.addFileList(searchList)
        }

        searchViewModel.searchResults.observe(viewLifecycleOwner) {

            if (!swipeRefreshLayout.isRefreshing) return@observe

            it?.let { apiResponse ->

                if (apiResponse.isSuccess()) {
                    updateMostRecentSearches()
                    val searchList = getSearchResults(apiResponse.data)

                    when {
                        searchViewModel.currentPage == 1 -> handleFirstResult(searchList)
                        searchList.isEmpty() -> handleNoResult()
                        searchList.size < ApiRepository.PER_PAGE -> handleLastPage(searchList)
                        else -> handleNewPage(searchList)
                    }
                } else {
                    handleApiCallFailure(apiResponse)
                }

            } ?: handleLiveDataTriggerWhenInitialized()

            isDownloading = false
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun updateMostRecentSearches() {
        val newSearch = searchView.text.toString().trim()
        if (newSearch.isEmpty()) return

        val newSearches = (listOf(newSearch) + recentSearchesAdapter.searches)
            .distinct()
            .take(MAX_MOST_RECENT_SEARCHES)
            .also(recentSearchesAdapter::setItems)

        recentSearchesContainer.isGone = newSearches.isEmpty()
    }

    private fun triggerSearch() {
        searchViewModel.currentPage = 1
        downloadFiles(true, false)
    }

    private fun removeFilter(filter: FilterKey, categoryId: Int?) {

        fun removeDateFilter() {
            searchViewModel.dateFilter = null
        }

        fun removeTypeFilter() {
            searchViewModel.typeFilter = null
        }

        fun removeCategoryFilter(categoryId: Int?) = with(searchViewModel) {
            if (categoryId != null) {
                categoriesFilter?.let { categories ->
                    val filteredCategories = categories.filter { it.id != categoryId }
                    categoriesFilter = if (filteredCategories.isEmpty()) null else filteredCategories
                }
            }
        }

        when (filter) {
            FilterKey.DATE -> removeDateFilter()
            FilterKey.TYPE -> removeTypeFilter()
            FilterKey.CATEGORIES -> removeCategoryFilter(categoryId)
            FilterKey.CATEGORIES_OWNERSHIP -> Unit // It's impossible to remove this filter by clicking on it
        }
        triggerSearch()
    }

    private fun updateUi(mode: VisibilityMode) {

        fun displayRecentSearches() {
            recentSearchesView.isVisible = true
            filtersRecyclerView.isGone = true
            noFilesLayout.isGone = true
            sortLayout.isGone = true
            fileRecyclerView.isGone = true
        }

        fun displayLoadingView() {
            recentSearchesView.isGone = true
            filtersRecyclerView.isGone = filtersAdapter.filters.isEmpty()
            noFilesLayout.isGone = true
            sortLayout.isGone = true
            fileRecyclerView.isGone = true
        }

        fun displaySearchResult(mode: VisibilityMode) {
            recentSearchesView.isGone = true
            filtersRecyclerView.isGone = filtersAdapter.filters.isEmpty()
            changeNoFilesLayoutVisibility(mode == VisibilityMode.NO_RESULTS, false)
        }

        when (mode) {
            VisibilityMode.RECENT_SEARCHES -> displayRecentSearches()
            VisibilityMode.LOADING -> displayLoadingView()
            VisibilityMode.NO_RESULTS, VisibilityMode.RESULTS -> displaySearchResult(mode)
        }
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_search_grey,
                title = R.string.searchNoFile,
                initialListView = fileRecyclerView,
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {

            val currentQuery = searchView?.text?.toString()?.trim() ?: ""

            if (currentQuery.isEmpty() && filtersAdapter.filters.isEmpty()) {
                fileAdapter.setFiles(arrayListOf())
                searchViewModel.visibilityMode.value = VisibilityMode.RECENT_SEARCHES
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = searchViewModel.searchOldFileList?.toMutableList() as? ArrayList
            if (oldList?.isNotEmpty() == true && fileAdapter.getFiles().isEmpty()) {
                fileAdapter.setFiles(oldList)
                searchViewModel.searchOldFileList = null
                searchViewModel.visibilityMode.value = VisibilityMode.RESULTS
                swipeRefreshLayout.isRefreshing = false
                return
            }

            swipeRefreshLayout.isRefreshing = true
            isDownloading = true
            searchViewModel.searchFileByName.value = currentQuery to fileListViewModel.sortType
        }
    }

    enum class VisibilityMode {
        RECENT_SEARCHES, LOADING, NO_RESULTS, RESULTS
    }

    private companion object {
        const val MAX_MOST_RECENT_SEARCHES = 5
    }
}
