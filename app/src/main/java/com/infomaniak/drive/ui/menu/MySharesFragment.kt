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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MySharesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID

class MySharesFragment : FileSubTypeListFragment() {

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false
    override var allowCancellation: Boolean = false

    override val noItemsRootIcon = R.drawable.ic_share
    override val noItemsRootTitle = R.string.mySharesNoFile

    private val navigationArgs: MySharesFragmentArgs by navArgs()
    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        setToolbarTitle(R.string.mySharesTitle)
        setupAdapter()
        setupSaveButton()
    }

    private fun initParams() {
        if (folderId == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderId = OTHER_ROOT_ID
        }
    }

    private fun setupAdapter() {
        fileAdapter.onFileClicked = { file ->
            fileListViewModel.cancelDownloadFiles()
            if (file.isFolder()) safeNavigate(
                MySharesFragmentDirections.actionMySharesFragmentSelf(
                    userDrive = navigationArgs.userDrive,
                    folderId = file.id,
                    folderName = file.name
                )
            ) else {
                val fileList = fileAdapter.getFileObjectsList(mainViewModel.realm)
                Utils.displayFile(mainViewModel, findNavController(), file, fileList)
            }
        }
        fileAdapter.isSelectingFolder = requireActivity() is SelectFolderActivity
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = MySharesMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            fileListViewModel.getMySharedFiles(fileListViewModel.sortType, navigationArgs.userDrive)
                .observe(viewLifecycleOwner) {
                    // forceClean because myShares is not paginated
                    populateFileList(
                        files = it?.first ?: ArrayList(),
                        folderId = FileController.MY_SHARES_FILE_ID,
                        forceClean = true,
                        isComplete = true,
                        realm = mainViewModel.realm,
                        isNewSort = isNewSort
                    )
                }
        }
    }
}
