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

import android.os.Bundle
import android.view.View
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.get
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.item_search_view.*
import kotlinx.android.synthetic.main.search_filter.view.*
import java.util.*

class SearchFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false

    private lateinit var filterLayoutView: View
    private var isDownloading = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fileListViewModel.sortType = File.SortType.RECENT
        downloadFiles = DownloadFiles()
        filterLayoutView = layoutInflater.inflate(R.layout.search_filter, null)
        super.onViewCreated(view, savedInstanceState)

        noFilesLayout.setup(
            icon = R.drawable.ic_search_grey,
            title = R.string.searchNoFile,
            initialListView = fileRecyclerView
        )

        collapsingToolbarLayout.title = getString(R.string.searchTitle)
        searchViewCard.visibility = VISIBLE
        fileListLayout.addView(filterLayoutView, 1)

        clearButton.setOnClickListener { searchView.text = null }

        searchView.hint = getString(R.string.searchViewHint)
        searchView.addTextChangedListener(DebouncingTextWatcher(lifecycle) {
            clearButton?.visibility = if (it.isNullOrEmpty()) INVISIBLE else VISIBLE
            fileListViewModel.currentPage = 1
            downloadFiles(true)
        })
        searchView.setOnEditorActionListener { _, actionId, _ ->
            if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                fileListViewModel.currentPage = 1
                downloadFiles(true)
                true
            } else false
        }

        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete && !isDownloading) {
                fileAdapter.showLoading()
                fileListViewModel.currentPage++
                downloadFiles(true)
            }
        })

        fileAdapter.onFileClicked = { file ->
            if (file.isFolder()) {
                fileListViewModel.cancelDownloadFiles()
                safeNavigate(
                    SearchFragmentDirections.actionSearchFragmentToFileListFragment(file.id, file.name)
                )
            } else Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems())
        }

        filterLayoutView.apply {
            imagefilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.IMAGE) }
            videoFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.VIDEO) }
            audioFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.AUDIO) }
            pdfFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.PDF) }
            docsFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.TEXT) }
            pointsfilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.PRESENTATION) }
            gridsFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.SPREADSHEET) }
            folderFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.FOLDER) }
            archiveFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.ARCHIVE) }
            codeFilterLayout.setOnClickListener { updateFilter(it, File.ConvertedType.CODE) }
        }

        convertedTypeClose.setOnClickListener {
            convertedTypeLayout.visibility = GONE
            fileListViewModel.currentConvertedType = null
            fileListViewModel.currentConvertedTypeText = null
            fileListViewModel.currentConvertedTypeDrawable = null
            downloadFiles(true)
        }

        convertedType.text = fileListViewModel.currentConvertedTypeText
        convertedTypeIcon.setImageDrawable(fileListViewModel.currentConvertedTypeDrawable)
        showFilterLayout(true)

        observeSearchResult()
    }

    private fun observeSearchResult() {
        fileListViewModel.searchResults.observe(viewLifecycleOwner) {
            it?.let { apiResponse ->
                if (apiResponse.isSuccess()) {
                    val searchList = apiResponse.data ?: arrayListOf()
                    when {
                        fileListViewModel.currentPage == 1 -> {
                            fileAdapter.setList(searchList)
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

    private fun updateFilter(view: View, type: File.ConvertedType) {
        val cardView = view as ConstraintLayout
        fileListViewModel.currentConvertedTypeDrawable = (cardView[0] as ImageView).drawable
        fileListViewModel.currentConvertedTypeText = (cardView[1] as TextView).text?.toString()

        convertedType.text = fileListViewModel.currentConvertedTypeText
        convertedTypeIcon.setImageDrawable(fileListViewModel.currentConvertedTypeDrawable)
        convertedTypeLayout.visibility = VISIBLE
        fileListViewModel.currentPage = 1
        fileListViewModel.currentConvertedType = type.name.lowercase(Locale.ROOT)
        downloadFiles(true)
    }

    override fun onPause() {
        fileListViewModel.oldList = fileAdapter.getItems()
        searchView.isFocusable = false
        super.onPause()
    }

    private fun showFilterLayout(show: Boolean) {
        if (show) {
            convertedTypeLayout.visibility = GONE
            fileRecyclerView.visibility = GONE
            filterLayoutView.visibility = VISIBLE
            sortLayout.visibility = GONE
        } else {
            fileRecyclerView.visibility = VISIBLE
            filterLayoutView.visibility = GONE
            sortLayout.visibility = VISIBLE
        }
    }


    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            swipeRefreshLayout.isRefreshing = true
            val currentQuery = searchView?.text?.toString()

            if (currentQuery.isNullOrEmpty() && fileListViewModel.currentConvertedType == null) {
                fileAdapter.setList(arrayListOf())
                showFilterLayout(true)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val oldList = fileListViewModel.oldList?.toMutableList() as? ArrayList
            if (!oldList.isNullOrEmpty() && fileAdapter.getItems().isEmpty()) {
                fileAdapter.setList(oldList)
                fileListViewModel.oldList = null
                if (fileListViewModel.currentConvertedType != null) convertedTypeLayout.visibility = VISIBLE
                showFilterLayout(false)
                swipeRefreshLayout.isRefreshing = false
                return
            }

            isDownloading = true
            showFilterLayout(false)
            fileListViewModel.searchFileByName.value = currentQuery
        }
    }
}