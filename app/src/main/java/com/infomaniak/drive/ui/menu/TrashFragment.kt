/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackTrashEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.SortTypeUsage
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.TrashMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.openKSuiteProBottomSheet
import com.infomaniak.drive.utils.openMyKSuiteUpgradeBottomSheet
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate

class TrashFragment : FileSubTypeListFragment() {

    val trashViewModel: TrashViewModel by navGraphViewModels(R.id.trashFragment)
    private val navigationArgs: TrashFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override val sortTypeUsage = SortTypeUsage.TRASH

    override val noItemsRootIcon = R.drawable.ic_bin
    override val noItemsRootTitle = R.string.trashNoFile

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navigationArgs.subfolderId.takeIf { it != -1 }?.let {
            // TODO Load the subfolder
            //safeNavigate(TrashFragmentDirections.actionTrashFragmentSelf(it, "FOLDERNAME"))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initParams()
        super.onViewCreated(view, savedInstanceState)
        setupTrashEmptying()

        setToolbarTitle(R.string.trashTitle)

        setupAdapter()

        trashViewModel.removeFileId.observe(viewLifecycleOwner) { fileToRemove ->
            removeFileFromAdapter(fileToRemove)
        }

        setupBasicMultiSelectLayout()
        observeDriveTrash()
        observeTrashedFolderFiles()

        setupAutoClearUpgradeLayout()
    }

    private fun initParams() = with(binding) {
        fileListViewModel.sortType = SortType.RECENT_TRASHED
        sortFiles = SortFiles()
        downloadFiles = DownloadFiles(
            if (folderId == ROOT_ID) null else File(id = folderId, driveId = AccountUtils.currentDriveId, name = folderName)
        )
        noFilesLayout.viewsToDisable = if (folderId == ROOT_ID) listOf(emptyTrash) else null
    }

    private fun setupTrashEmptying() {
        binding.emptyTrash.setupEmptyTrashButton()
        multiSelectLayout?.emptyTrashButton?.setupEmptyTrashButton()
    }

    private fun setupAdapter() {
        fileAdapter.apply {
            showShareFileButton = false
            onFileClicked = { file ->
                trashViewModel.cancelTrashFileJob()
                if (file.isFolder()) {
                    safeNavigate(TrashFragmentDirections.actionTrashFragmentSelf(file.id, file.name))
                } else {
                    showTrashedFileActions(file)
                }
            }
            onMenuClicked = { file -> showTrashedFileActions(file) }
        }
    }

    private fun MaterialButton.setupEmptyTrashButton() {
        isVisible = true
        if (folderId != ROOT_ID) isEnabled = false
        setOnClickListener {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.buttonEmptyTrash),
                message = getString(R.string.modalEmptyTrashDescription),
                isDeletion = true,
                autoDismiss = false,
            ) { dialog ->
                trackTrashEvent(MatomoName.EmptyTrash)
                closeMultiSelect()
                trashViewModel.emptyTrash(AccountUtils.currentDriveId).observe(viewLifecycleOwner) { apiResponse ->
                    dialog.dismiss()
                    if (apiResponse.data == true) {
                        showSnackbar(titleId = R.string.snackbarEmptyTrashConfirmation, showAboveFab = true)
                        onRefresh()
                    } else {
                        showSnackbar(apiResponse.translateError(), showAboveFab = true)
                    }
                }
            }
        }
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = TrashMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    override fun closeMultiSelect() {
        super.closeMultiSelect()
        binding.swipeRefreshLayout.isEnabled = true
    }

    private fun showTrashedFileActions(file: File) {
        trashViewModel.selectedFile.value = file
        safeNavigate(R.id.trashedFileActionsBottomSheetDialog)
    }

    private fun removeFileFromAdapter(fileId: Int) {
        fileAdapter.deleteByFileId(fileId)
        binding.noFilesLayout.toggleVisibility(fileAdapter.getFiles().isEmpty())
    }

    private fun observeDriveTrash() {
        trashViewModel.driveTrashResults.observe(viewLifecycleOwner) { result ->
            populateFileList(
                files = result?.files ?: ArrayList(),
                isComplete = result?.isComplete ?: true,
                forceClean = result?.isFirstPage == true,
                isNewSort = result?.isNewSort ?: false,
            )
        }
    }

    private fun observeTrashedFolderFiles() {
        trashViewModel.trashedFolderFilesResults.observe(viewLifecycleOwner) {
            it?.let { result ->
                populateFileList(
                    files = result.files,
                    isComplete = result.isComplete,
                    forceClean = result.isFirstPage,
                    isNewSort = result.isNewSort,
                )
            }
        }
    }

    private fun setupAutoClearUpgradeLayout() = with(binding) {
        val drive = AccountUtils.getCurrentDrive() ?: return

        trashAutoClearLayout.isVisible = drive.isKSuiteFreeTier
        myKSuitePlusChip.isVisible = drive.isKSuitePersoFree
        kSuiteProChip.isVisible = drive.isKSuiteProFree

        trashAutoClearUpgradeButton.setOnClickListener {
            val matomoName = "trashStorageLimit"
            if (drive.isKSuiteProFree) {
                openKSuiteProBottomSheet(drive.kSuite!!, drive.isAdmin, matomoName)
            } else {
                openMyKSuiteUpgradeBottomSheet(matomoName)
            }
        }
    }

    private inner class SortFiles : () -> Unit {
        override fun invoke() {
            getBackNavigationResult<SortType>(SORT_TYPE_OPTION_KEY) { newSortType ->
                fileListViewModel.sortType = when (newSortType) {
                    SortType.OLDER -> SortType.OLDER_TRASHED
                    SortType.RECENT -> SortType.RECENT_TRASHED
                    else -> newSortType
                }
                binding.sortButton.setText(fileListViewModel.sortType.translation)
                downloadFiles(true, true)
            }
        }
    }

    private inner class DownloadFiles() : (Boolean, Boolean) -> Unit {

        private var folder: File? = null

        constructor(folder: File?) : this() {
            this.folder = folder
        }

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (ignoreCache) fileAdapter.setFiles(arrayListOf())
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            folder?.let { folder ->
                trashViewModel.loadTrashedFolderFiles(
                    file = folder,
                    order = fileListViewModel.sortType,
                    isNewSort = isNewSort,
                )
            } ?: run {
                trashViewModel.loadDriveTrash(AccountUtils.currentDriveId, fileListViewModel.sortType, isNewSort)
            }
        }
    }
}
