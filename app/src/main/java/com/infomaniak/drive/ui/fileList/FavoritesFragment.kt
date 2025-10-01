/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.multiSelect.FavoritesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID

class FavoritesFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override val noItemsRootIcon = R.drawable.ic_star_filled
    override val noItemsRootTitle = R.string.favoritesNoFile

    private val navigationArgs: FavoritesFragmentArgs by navArgs()

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        setToolbarTitle(R.string.favoritesTitle)
        setupAdapter()
        setupMultiSelectLayout()
        setupSaveButton()
    }

    private fun initParams() {
        if (folderId == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderId = OTHER_ROOT_ID
        }
    }

    private fun setupAdapter() {
        fileAdapter.apply {
            isSelectingFolder = requireActivity() is SelectFolderActivity
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
        safeNavigate(FavoritesFragmentDirections.actionFavoritesFragmentSelf(navigationArgs.userDrive, id, name))
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
        folderId: Int?,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(
            type,
            folderId,
            areAllFromTheSameFolder = false,
            allSelectedFilesCount,
            destinationFolder,
            color
        )
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false
            fileListViewModel.getFavoriteFiles(
                order = fileListViewModel.sortType,
                isNewSort = isNewSort,
                userDrive = navigationArgs.userDrive
            ).observe(viewLifecycleOwner) {
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
                        changeNoFilesLayoutVisibility(realmFiles.isEmpty(), changeControlsVisibility = false)
                    }
                    fileAdapter.isComplete = result.isComplete
                } ?: run {
                    changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, changeControlsVisibility = false)
                    fileAdapter.isComplete = true
                }
                showLoadingTimer.cancel()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}
