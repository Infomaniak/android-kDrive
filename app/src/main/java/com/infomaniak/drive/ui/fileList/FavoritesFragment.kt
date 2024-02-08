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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.multiSelect.FavoritesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.utils.safeNavigate

class FavoritesFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override val noItemsRootIcon = R.drawable.ic_star_filled
    override val noItemsRootTitle = R.string.favoritesNoFile

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        setToolbarTitle(R.string.favoritesTitle)
        setupAdapter()
        setupMultiSelectLayout()
    }

    private fun initParams() {
        if (folderId == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderId = OTHER_ROOT_ID
        }
    }

    private fun setupAdapter() {
        fileAdapter.apply {
            onEmptyList = { changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = false) }

            onFileClicked = { file ->
                if (file.isFolder()) {
                    file.openFavoriteFolder()
                } else {
                    val fileList = getFileObjectsList(mainViewModel.realm)
                    Utils.displayFile(mainViewModel, findNavController(), file, fileList)
                }
            }
        }
    }

    private fun File.openFavoriteFolder() {
        fileListViewModel.cancelDownloadFiles()
        safeNavigate(FavoritesFragmentDirections.actionFavoritesFragmentSelf(id, name))
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.selectAllButton?.isGone = true
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = FavoritesMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(type, false, allSelectedFilesCount, destinationFolder, color)
    }

    companion object {
        const val MATOMO_CATEGORY = "favoritesFileAction"
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false
            fileListViewModel.getFavoriteFiles(fileListViewModel.sortType, isNewSort).observe(viewLifecycleOwner) {
                it?.let { result ->
                    if (fileAdapter.itemCount == 0 || result.isFirstPage || isNewSort) {
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
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}
