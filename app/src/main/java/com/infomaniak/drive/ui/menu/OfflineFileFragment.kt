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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.multiSelect.OfflineMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.Utils
import kotlinx.android.synthetic.main.fragment_file_list.*

open class OfflineFileFragment : FileSubTypeListFragment() {

    override var enabledMultiSelectMode: Boolean = true
    override var allowCancellation: Boolean = false

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout? = swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        collapsingToolbarLayout.title = getString(R.string.offlineFileTitle)
        setupMultiSelectLayout()
    }

    private fun initParams() {
        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        folderId = Utils.OTHER_ROOT_ID
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.selectAllButton?.isGone = true
    }

    override fun onMenuButtonClicked() {
        val (fileIds, onlyFolders, onlyFavorite, onlyOffline, isAllSelected) = multiSelectManager.getMenuNavArgs()
        OfflineMultiSelectActionsBottomSheetDialog().apply {
            arguments = MultiSelectActionsBottomSheetDialogArgs(
                fileIds = fileIds,
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
                isAllSelected = isAllSelected
            ).toBundle()
        }.show(childFragmentManager, "ActionOfflineMultiSelectBottomSheetDialog")
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
        const val MATOMO_CATEGORY = "offlineFileAction"
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_offline,
                title = R.string.offlineFileNoFile,
                description = R.string.offlineFileNoFileDescription,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (fileAdapter.fileList.isEmpty() || isNewSort) {
                FileController.getOfflineFiles(order = fileListViewModel.sortType, customRealm = mainViewModel.realm).apply {
                    fileAdapter.updateFileList(this)
                }
            }

            fileAdapter.isComplete = true
            swipeRefreshLayout.isRefreshing = false
            changeNoFilesLayoutVisibility(
                fileAdapter.fileList.isEmpty(),
                changeControlsVisibility = true,
                ignoreOffline = true
            )
        }
    }
}
