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

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.multiSelect.FavoritesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialogArgs
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class FavoritesFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        collapsingToolbarLayout.title = getString(R.string.favoritesTitle)
        setupAdapter()
        setupMultiSelectLayout()
        setupMultiSelectOpening()
    }

    private fun initParams() {
        if (folderId == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderId = OTHER_ROOT_ID
        }
        setNoFilesLayout = SetNoFilesLayout()
    }

    private fun setupAdapter() {
        fileAdapter.apply {
            onEmptyList = { changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = false) }

            onFileClicked = { file ->
                if (file.isFolder()) {
                    fileListViewModel.cancelDownloadFiles()
                    safeNavigate(FavoritesFragmentDirections.actionFavoritesFragmentSelf(file.id, file.name))
                } else {
                    val fileList = getFileObjectsList(mainViewModel.realm)
                    Utils.displayFile(mainViewModel, findNavController(), file, fileList)
                }
            }
        }
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.selectAllButton?.isGone = true
    }

    private fun setupMultiSelectOpening() {
        multiSelectManager.openMultiSelect = {
            swipeRefreshLayout?.isEnabled = false
            openMultiSelect()
        }
    }

    override fun onMenuButtonClicked() {
        val (fileIds, onlyFolders, onlyFavorite, onlyOffline, isAllSelected) = multiSelectManager.getMenuNavArgs()
        FavoritesMultiSelectActionsBottomSheetDialog().apply {
            arguments = MultiSelectActionsBottomSheetDialogArgs(
                fileIds = fileIds,
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
                isAllSelected = isAllSelected
            ).toBundle()
        }.show(childFragmentManager, "ActionFavoritesMultiSelectBottomSheetDialog")
    }

    override fun closeMultiSelect() {
        super.closeMultiSelect()
        swipeRefreshLayout?.isEnabled = true
    }

    companion object {
        const val MATOMO_CATEGORY = "favoritesFileAction"
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_star_filled,
                title = R.string.favoritesNoFile,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false
            fileListViewModel.getFavoriteFiles(fileListViewModel.sortType).observe(viewLifecycleOwner) {
                it?.let { result ->
                    if (fileAdapter.itemCount == 0 || result.page == 1 || isNewSort) {
                        val realmFiles = FileController.getRealmLiveFiles(
                            isFavorite = true,
                            order = fileListViewModel.sortType,
                            parentId = FileController.FAVORITES_FILE_ID,
                            realm = mainViewModel.realm,
                            withVisibilitySort = false
                        )
                        fileAdapter.updateFileList(realmFiles)
                        changeNoFilesLayoutVisibility(realmFiles.isEmpty(), false)
                    }
                    fileAdapter.isComplete = result.isComplete
                } ?: run {
                    changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, false)
                    fileAdapter.isComplete = true
                }
                showLoadingTimer.cancel()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}

