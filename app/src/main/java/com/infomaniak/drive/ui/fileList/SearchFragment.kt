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
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.utils.setPagination
import io.realm.RealmList
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.item_search_view.*
import kotlinx.android.synthetic.main.search_filter.*
import java.util.*
import kotlin.collections.LinkedHashMap

class SearchFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false

    private lateinit var previousSearchesAdapter: SearchAdapter
    private lateinit var filterLayoutView: View
    private var isDownloading = false

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fileListViewModel.sortType = File.SortType.RECENT

        // Get preview List if needed
        if (mainViewModel.currentPreviewFileList.isNotEmpty()) {
            fileListViewModel.searchOldFileList = RealmList(*mainViewModel.currentPreviewFileList.values.toTypedArray())
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }

        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        filterLayoutView = layoutInflater.inflate(R.layout.search_filter, null)
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = getString(R.string.searchTitle)
        searchViewCard.isVisible = true
        fileListLayout.addView(filterLayoutView, 1)

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

        convertedTypeClose.setOnClickListener {
            convertedTypeLayout.isGone = true
            fileListViewModel.currentConvertedType = null
            fileListViewModel.currentConvertedTypeText = null
            fileListViewModel.currentConvertedTypeDrawable = null
            downloadFiles(true, false)
        }

        convertedType.text = fileListViewModel.currentConvertedTypeText
        convertedTypeIcon.setImageDrawable(fileListViewModel.currentConvertedTypeDrawable)

        if (fileAdapter.fileList.isEmpty()) {
            showFilterLayout(true)
        }

        setSearchesAdapter()
        observeSearchResult()
    }

    private fun setSearchesAdapter() {
        previousSearchesAdapter = SearchAdapter { searchView.setText(it) }.apply {
            setAll(AppSettings.mostRecentSearches)
        }
        recentSearchesList.adapter = previousSearchesAdapter
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

    private fun updateMostRecentSearches() {
        val newSearch = searchView.text.toString()
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

    override fun onPause() {
        fileListViewModel.searchOldFileList = fileAdapter.getFiles()
        searchView.isFocusable = false
        super.onPause()
    }

    private fun showFilterLayout(show: Boolean) {
        if (show) {
            changeNoFilesLayoutVisibility(hideFileList = false, changeControlsVisibility = false)
            convertedTypeLayout.isGone = true
            fileRecyclerView.isGone = true
            filterLayoutView.isVisible = true
            sortLayout.isGone = true
        } else {
            fileRecyclerView.isVisible = true
            filterLayoutView.isGone = true
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

            if (currentQuery.isNullOrEmpty() && fileListViewModel.currentConvertedType == null) {
                fileAdapter.setFiles(arrayListOf())
                showFilterLayout(true)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = fileListViewModel.searchOldFileList?.toMutableList() as? ArrayList
            if (!oldList.isNullOrEmpty() && fileAdapter.getFiles().isEmpty()) {
                fileAdapter.setFiles(oldList)
                fileListViewModel.searchOldFileList = null
                if (fileListViewModel.currentConvertedType != null) convertedTypeLayout.isVisible = true
                showFilterLayout(false)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            isDownloading = true
            showFilterLayout(false)
            fileListViewModel.searchFileByName.value = currentQuery
        }
    }

    private companion object {
        const val MAX_MOST_RECENT_SEARCHES = 5
    }
}
