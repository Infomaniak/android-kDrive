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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.bottomSheetDialogs.DriveMaintenanceBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SharedWithMeViewModel
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.SharedWithMeMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.utils.safeNavigate

class SharedWithMeFragment : FileSubTypeListFragment() {

    private val navigationArgs: SharedWithMeFragmentArgs by navArgs()
    private val sharedWithMeViewModel: SharedWithMeViewModel by viewModels()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override val noItemsRootIcon = R.drawable.ic_share
    override val noItemsRootTitle = R.string.sharedWithMeNoFile

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isRoot = folderId == ROOT_ID
        mainViewModel.setCurrentFolder(null)
        userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = true).also {
            mainViewModel.loadCurrentFolder(
                folderId = if (isRoot) FileController.SHARED_WITH_ME_FILE_ID else folderId,
                userDrive = it,
            )
        }

        downloadFiles = DownloadFiles()

        fileListViewModel.isSharedWithMe = true
        super.onViewCreated(view, savedInstanceState)

        binding.collapsingToolbarLayout.title = if (isRoot) {
            getString(R.string.sharedWithMeTitle)
        } else {
            navigationArgs.folderName
        }

        fileAdapter.apply {
            isSelectingFolder = requireActivity() is SelectFolderActivity
            initAsyncListDiffer()
            onFileClicked = { file ->
                fileListViewModel.cancelDownloadFiles()
                when {
                    // Before APIv3, we could have a File with a type drive. Now, a File cannot have a type drive. We moved the
                    // maintenance check on the folder type but we don't know if this is necessary
                    file.isFolder() -> {
                        DriveInfosController.getDrive(AccountUtils.currentUserId, file.driveId)?.let { currentDrive ->
                            if (currentDrive.maintenance) openMaintenanceDialog(currentDrive.name) else file.openSharedWithMeFolder()
                        }
                    }
                    else -> {
                        val fileList = fileAdapter.getFileObjectsList(sharedWithMeViewModel.sharedWithMeRealm)
                        Utils.displayFile(mainViewModel, findNavController(), file, fileList, isSharedWithMe = true)
                    }
                }
            }
        }

        sharedWithMeViewModel.sharedWithMeFiles.observe(viewLifecycleOwner) { files ->
            populateFileList(
                files = ArrayList(files),
                isComplete = true,
                isNewSort = true
            )
        }

        setupBasicMultiSelectLayout()
    }

    private fun openMaintenanceDialog(driveName: String) {
        safeNavigate(
            R.id.driveMaintenanceBottomSheetFragment,
            DriveMaintenanceBottomSheetDialogArgs(driveName).toBundle()
        )
    }

    private fun File.openSharedWithMeFolder() {
        safeNavigate(
            SharedWithMeFragmentDirections.actionSharedWithMeFragmentSelf(
                folderId = id,
                folderName = name,
                driveId = driveId,
            )
        )
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = SharedWithMeMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (ignoreCache && !fileAdapter.fileList.isManaged) fileAdapter.setFiles(arrayListOf())
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            sharedWithMeViewModel.loadSharedWithMeFiles(
                parentId = folderId,
                order = fileListViewModel.sortType,
                userDrive = userDrive ?: UserDrive(sharedWithMe = true),
                isNewSort = isNewSort,
            )
        }
    }
}
