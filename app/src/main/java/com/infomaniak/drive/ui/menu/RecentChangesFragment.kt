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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.core.legacy.utils.setPagination
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.file.SpecialFolder.RecentChanges
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.RecentChangesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.Utils

class RecentChangesFragment : FileSubTypeListFragment() {

    private val recentChangesViewModel: RecentChangesViewModel by viewModels()

    override var enabledMultiSelectMode: Boolean = true

    override val noItemsRootIcon = R.drawable.ic_clock
    override val noItemsRootTitle = R.string.homeNoActivities

    private var isDownloadingChanges = false
    private val navArgs by navArgs<RecentChangesFragmentArgs>()

    override val fileIdToPreview: Int
        get() = navArgs.previewFileId


    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        initParams()
        super.onViewCreated(view, savedInstanceState)

        fileRecyclerView.apply {
            setPagination({
                if (!fileAdapter.isComplete && !isDownloadingChanges) {
                    startLoading()
                    recentChangesViewModel.loadNextPage()
                }
            })
        }

        sortButton.isGone = true
        setToolbarTitle(R.string.lastEditsTitle)

        observeRecentChanges()
    }

    private fun initParams() {
        downloadFiles = DownloadFiles()
        folderId = Utils.OTHER_ROOT_ID
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = RecentChangesMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    private fun startLoading() {
        showLoadingTimer.start()
        isDownloadingChanges = true
    }

    private fun observeRecentChanges() {
        recentChangesViewModel.recentChangesResults.observe(viewLifecycleOwner) { result ->
            populateFileList(
                files = result?.files ?: arrayListOf(),
                folderId = RecentChanges.id,
                ignoreOffline = true,
                isComplete = result?.isComplete ?: true,
                realm = mainViewModel.realm,
                isNewSort = recentChangesViewModel.isNewSort,
            )
            isDownloadingChanges = false
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            startLoading()
            fileAdapter.isComplete = false

            recentChangesViewModel.loadRecentChanges(isNewSort)
        }
    }
}
