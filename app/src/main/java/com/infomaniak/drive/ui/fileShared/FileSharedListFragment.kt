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
import com.infomaniak.drive.ui.fileShared.FileSharedViewModel.Companion.ROOT_SHARED_FILE_ID
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmark
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.whenResultIsOk
import com.infomaniak.lib.core.R as RCore

class FileSharedListFragment : FileListFragment() {

    private val fileSharedViewModel: FileSharedViewModel by activityViewModels()
    private val navigationArgs: FileSharedListFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    private var drivePermissions: DrivePermissions? = null
    private val selectDriveAndFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk(::onDriveAndFolderSelected)
    }

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@FileSharedListFragment) { authorized -> if (authorized) downloadAllFiles() }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

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

        binding.toolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.downloadAllFiles) downloadAllFiles()
                true
            }

            setNavigationOnClickListener { onBackPressed() }
            menu?.findItem(R.id.downloadAllFiles)?.isVisible = true
        }

        (requireActivity() as? FileSharedActivity)?.let { parentActivity ->
            parentActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }
            setMainButton(parentActivity.getMainButton())
        }

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

    private fun downloadAllFiles() {
        // RootSharedFile can either be a folder or a single file
        fileSharedViewModel.rootSharedFile.value?.let { file ->
            drivePermissions?.let { permissions -> requireContext().downloadFile(permissions, file) }
        }
    }

    private fun onDriveAndFolderSelected(data: Intent?) {
        val destinationDriveId = data?.getIntExtra(DESTINATION_DRIVE_ID_KEY, DEFAULT_ID) ?: DEFAULT_ID
        val destinationFolderId = data?.getIntExtra(DESTINATION_FOLDER_ID_KEY, DEFAULT_ID) ?: DEFAULT_ID

        if (data == null || destinationDriveId == DEFAULT_ID || destinationFolderId == DEFAULT_ID) {
            showSnackbar(RCore.string.anErrorHasOccurred)
        } else {
            fileSharedViewModel.importFilesToDrive(
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
        const val MATOMO_CATEGORY = "FileSharedListAction"
        private const val DEFAULT_ID = -1
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
