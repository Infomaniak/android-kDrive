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
package com.infomaniak.drive.ui.publicShare

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivity.Companion.DESTINATION_DRIVE_ID_KEY
import com.infomaniak.drive.ui.SaveExternalFilesActivity.Companion.DESTINATION_FOLDER_ID_KEY
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.publicShare.PublicShareViewModel.Companion.ROOT_SHARED_FILE_ID
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmark
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.whenResultIsOk
import com.infomaniak.lib.core.R as RCore

class PublicShareListFragment : FileListFragment() {

    private val publicShareViewModel: PublicShareViewModel by activityViewModels()
    private val navigationArgs: PublicShareListFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    private var drivePermissions = DrivePermissions()
    private val selectDriveAndFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk(::onDriveAndFolderSelected)
    }

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        drivePermissions.registerPermissions(this@PublicShareListFragment) { authorized -> if (authorized) downloadAllFiles() }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        folderName = navigationArgs.fileName
        folderId = navigationArgs.fileId
        downloadFiles = DownloadFiles()

        super.onViewCreated(view, savedInstanceState)

        setToolbarTitle(R.string.sharedWithMeTitle)
        binding.uploadFileInProgressView.isGone = true

        fileAdapter.apply {
            initAsyncListDiffer()
            onMenuClicked = ::onMenuClicked
            onFileClicked = ::onFileClicked
        }

        setupMultiSelectLayout()

        binding.toolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.downloadAllFiles) downloadAllFiles()
                true
            }

            setNavigationOnClickListener { onBackPressed() }
            menu?.findItem(R.id.downloadAllFiles)?.isVisible = true
        }

        (requireActivity() as? PublicShareActivity)?.let { parentActivity ->
            parentActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }
            setMainButton(parentActivity.getMainButton())
        }

        multiSelectManager.currentFolder = publicShareViewModel.fileClicked
        mainViewModel.setCurrentFolder(multiSelectManager.currentFolder)

        observeRootFile()
        observeFiles()
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = PublicShareMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = true,
        )
    }

    private fun onMenuClicked(file: File) {
        if (file.isUsable()) {
            publicShareViewModel.fileClicked = file
            safeNavigate(R.id.publicShareFileActionsBottomSheet)
        }
    }

    private fun onFileClicked(file: File) {
        if (file.isUsable()) {
            publicShareViewModel.fileClicked = file
            when {
                file.isFolder() -> openFolder(file)
                file.isBookmark() -> openBookmark(file)
                else -> displayFile(file, mainViewModel, fileAdapter, publicShareViewModel.publicShareUuid)
            }
        }
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
        publicShareViewModel.cancelDownload()
        if (folderId == publicShareViewModel.rootSharedFile.value?.id || folderId == ROOT_SHARED_FILE_ID) {
            requireActivity().finish()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun observeFiles() {
        publicShareViewModel.childrenLiveData.observe(viewLifecycleOwner) { (files, shouldUpdate) ->
            if (shouldUpdate) populateFileList(files)
        }
    }

    private fun observeRootFile() {
        publicShareViewModel.rootSharedFile.observe(viewLifecycleOwner) { file ->
            publicShareViewModel.fileClicked = file
            if (file?.isFolder() == true) {
                openFolder(file)
            } else {
                val fileList = file?.let(::listOf) ?: listOf()
                publicShareViewModel.childrenLiveData.postValue(fileList to true)
            }
        }
    }

    private fun openFolder(folder: File) {
        openFolder(
            file = folder,
            shouldHideBottomNavigation = true,
            shouldShowSmallFab = false,
            fileListViewModel = fileListViewModel,
            isPublicSharedFile = true,
        )
    }

    private fun downloadAllFiles() {
        // RootSharedFile can either be a folder or a single file
        publicShareViewModel.rootSharedFile.value?.let { file -> requireContext().downloadFile(drivePermissions, file) }
    }

    private fun onDriveAndFolderSelected(data: Intent?) {
        val destinationDriveId = data?.getIntExtra(DESTINATION_DRIVE_ID_KEY, DEFAULT_ID) ?: DEFAULT_ID
        val destinationFolderId = data?.getIntExtra(DESTINATION_FOLDER_ID_KEY, DEFAULT_ID) ?: DEFAULT_ID

        if (data == null || destinationDriveId == DEFAULT_ID || destinationFolderId == DEFAULT_ID) {
            showSnackbar(RCore.string.anErrorHasOccurred)
        } else {
            publicShareViewModel.importFilesToDrive(
                destinationDriveId = destinationDriveId,
                destinationFolderId = destinationFolderId,
                fileIds = multiSelectManager.selectedItemsIds.toList(),
                exceptedFileIds = multiSelectManager.exceptedItemsIds,
            )
        }
    }

    private fun setMainButton(importButton: MaterialButton) {
        importButton.setOnClickListener {
            if (AccountUtils.currentDriveId == -1) {
                // TODO : Show bottomsheet to get app if this functionality is implemented by the back
                Intent(requireActivity(), LoginActivity::class.java).also(::startActivity)
            } else {
                Intent(requireActivity(), SaveExternalFilesActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    putExtras(
                        SaveExternalFilesActivityArgs(
                            userId = AccountUtils.currentUserId,
                            driveId = AccountUtils.currentDriveId,
                            isPublicShare = true,
                        ).toBundle()
                    ).also(selectDriveAndFolderResultLauncher::launch)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_ID = -1
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false
            publicShareViewModel.childrenLiveData.value = emptyList<File>() to false

            with(publicShareViewModel) {
                if (folderId == ROOT_SHARED_FILE_ID || rootSharedFile.value == null) {
                    downloadPublicShareRootFile()
                } else {
                    getFiles(folderId, fileListViewModel.sortType)
                }
            }
        }
    }
}
