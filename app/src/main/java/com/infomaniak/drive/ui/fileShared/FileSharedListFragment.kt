/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileShared

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmark
import com.infomaniak.drive.utils.FilePresenter.openFolder


class FileSharedListFragment : FileListFragment() {

    private val fileShareViewModel: FileSharedViewModel by activityViewModels()
    private val navigationArgs: FileSharedListFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()

        super.onViewCreated(view, savedInstanceState)

        binding.collapsingToolbarLayout.title = navigationArgs.fileName.ifBlank { getString(R.string.sharedWithMeTitle) }
        binding.uploadFileInProgressView.isGone = true

        fileAdapter.initAsyncListDiffer()
        fileAdapter.onFileClicked = { file ->
            if (file.isUsable()) {
                when {
                    file.isFolder() -> {
                        openFolder(
                            file = file,
                            shouldHideBottomNavigation = true,
                            shouldShowSmallFab = false,
                            fileListViewModel = fileListViewModel,
                            isSharedFile = true,
                        )
                    }
                    file.isBookmark() -> openBookmark(file)
                    else -> displayFile(file, mainViewModel, fileAdapter)
                }
            }
        }

        fileShareViewModel.childrenLiveData.observe(viewLifecycleOwner) { files ->
            populateFileList(files)
        }

        setupMultiSelectLayout()
    }

    private fun populateFileList(files: List<File>) {
        fileAdapter.setFiles(files)
        fileAdapter.isComplete = true
        showLoadingTimer.cancel()
        binding.swipeRefreshLayout.isRefreshing = false

        changeNoFilesLayoutVisibility(files.isEmpty(), changeControlsVisibility = true, ignoreOffline = false)
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.root?.isGone = true
    }

    companion object {
        const val MATOMO_CATEGORY = "FileSharedListAction"
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            with(fileShareViewModel) {
                if (navigationArgs.fileId == FileSharedViewModel.ERROR_ID || rootSharedFile == null) {
                    downloadSharedFile()
                } else {
                    downloadSharedFileChildren(navigationArgs.fileId, fileListViewModel.sortType)
                }
            }
        }
    }
}
