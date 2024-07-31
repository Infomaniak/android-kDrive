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
import androidx.activity.addCallback
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.fileShared.FileSharedViewModel.Companion.ROOT_SHARED_FILE_ID
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmark
import com.infomaniak.drive.utils.FilePresenter.openFolder

class FileSharedListFragment : FileListFragment() {

    private val fileSharedViewModel: FileSharedViewModel by activityViewModels()
    private val navigationArgs: FileSharedListFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        folderName = navigationArgs.fileName
        folderId = navigationArgs.fileId
        downloadFiles = DownloadFiles()

        super.onViewCreated(view, savedInstanceState)

        setToolbarTitle(R.string.sharedWithMeTitle)
        binding.uploadFileInProgressView.isGone = true

        fileAdapter.initAsyncListDiffer()
        fileAdapter.onFileClicked = { file ->
            if (file.isUsable()) {
                when {
                    file.isFolder() -> openFolder(file)
                    file.isBookmark() -> openBookmark(file)
                    else -> displayFile(file, mainViewModel, fileAdapter, shareLinkUuid = fileSharedViewModel.fileSharedLinkUuid)
                }
            }
        }

        setupMultiSelectLayout()

        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }

        observeRootFile()
        observeFiles()
    }

    private fun populateFileList(files: List<File>, shouldRefreshFiles: Boolean = true) {
        if (shouldRefreshFiles) fileAdapter.setFiles(files) else fileAdapter.addFileList(files)
        fileAdapter.isComplete = true
        showLoadingTimer.cancel()
        binding.swipeRefreshLayout.isRefreshing = false

        changeNoFilesLayoutVisibility(files.isEmpty(), changeControlsVisibility = true, ignoreOffline = false)
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.root?.isGone = true
    }

    private fun onBackPressed() {
        fileSharedViewModel.cancelDownload()
        if (folderId == fileSharedViewModel.rootSharedFile.value?.id || folderId == ROOT_SHARED_FILE_ID) {
            requireActivity().finish()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun observeFiles() {
        fileSharedViewModel.childrenLiveData.observe(viewLifecycleOwner) { (files, shouldUpdate) ->
            if (shouldUpdate) populateFileList(files)
        }
    }

    private fun observeRootFile() {
        fileSharedViewModel.rootSharedFile.observe(viewLifecycleOwner) { file ->
            if (file?.isFolder() == true) {
                openFolder(file)
            } else {
                val fileList = file?.let(::listOf) ?: listOf()
                fileSharedViewModel.childrenLiveData.postValue(fileList to true)
            }
        }
    }

    private fun openFolder(folder: File) {
        openFolder(
            file = folder,
            shouldHideBottomNavigation = true,
            shouldShowSmallFab = false,
            fileListViewModel = fileListViewModel,
            isSharedFile = true,
        )
    }

    companion object {
        const val MATOMO_CATEGORY = "FileSharedListAction"
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false
            fileSharedViewModel.childrenLiveData.value = emptyList<File>() to false

            with(fileSharedViewModel) {
                if (folderId == ROOT_SHARED_FILE_ID || rootSharedFile.value == null) {
                    downloadSharedFile()
                } else {
                    getFiles(folderId, fileListViewModel.sortType)
                }
            }
        }
    }
}
