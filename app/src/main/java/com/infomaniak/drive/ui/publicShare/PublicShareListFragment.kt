/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPublicShareActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileListNavigationType
import com.infomaniak.drive.data.models.coil.ImageLoaderType
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivity.Companion.DESTINATION_DRIVE_ID_KEY
import com.infomaniak.drive.ui.SaveExternalFilesActivity.Companion.DESTINATION_FOLDER_ID_KEY
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.preview.PreviewDownloadProgressDialogArgs
import com.infomaniak.drive.ui.publicShare.PublicShareViewModel.Companion.ROOT_SHARED_FILE_ID
import com.infomaniak.drive.ui.publicShare.PublicShareViewModel.PublicShareFilesResult
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmarkIntent
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.PublicShareUtils
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import com.infomaniak.core.legacy.R as RCore

class PublicShareListFragment : FileListFragment() {

    private val publicShareViewModel: PublicShareViewModel by activityViewModels()
    private val navigationArgs: PublicShareListFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    private val downloadPermissions = DrivePermissions(type = DrivePermissions.Type.DownloadingWithDownloadManager)
    private inline val importButton get() = (requireActivity() as PublicShareActivity).getMainButton()
    private val selectDriveAndFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk(::onDriveAndFolderSelected)
    }

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout = binding.swipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (publicShareViewModel.isPasswordNeeded && !publicShareViewModel.hasBeenAuthenticated) {
            safeNavigate(PublicShareListFragmentDirections.actionPublicShareListFragmentToPublicSharePasswordFragment())
        }

        if (publicShareViewModel.isExpired) {
            safeNavigate(PublicShareListFragmentDirections.actionPublicShareListFragmentToPublicShareOutdatedFragment())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        downloadPermissions.registerPermissions(this@PublicShareListFragment) { authorized -> if (authorized) downloadAllFiles() }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        folderName = navigationArgs.fileName
        folderId = navigationArgs.fileId
        publicShareViewModel.cancelDownload()
        downloadFiles = DownloadFiles()

        super.onViewCreated(view, savedInstanceState)

        setToolbarTitle(R.string.sharedWithMeTitle)
        binding.uploadFileInProgressView.isGone = true

        initFileAdapter()

        binding.toolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.downloadAllFiles) downloadAllFiles()
                true
            }

            setNavigationOnClickListener { onBackPressed() }
            menu?.findItem(R.id.downloadAllFiles)?.isVisible = publicShareViewModel.canDownloadFiles
        }

        (requireActivity() as? PublicShareActivity)?.let { parentActivity ->
            parentActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }
        }

        setMainButton()
        setupMultiSelect()

        observeRootFile()
        observeFiles()
        observeBookmarkAction()
        observeFilesImportation()
    }

    private fun initFileAdapter() {
        fileAdapter.apply {
            initAsyncListDiffer()
            onMenuClicked = ::onMenuClicked
            onFileClicked = ::onFileClicked
            publicShareCanDownload = publicShareViewModel.canDownloadFiles
        }
    }

    private fun setupMultiSelect() {
        setupBasicMultiSelectLayout()
        multiSelectManager.apply {
            isMultiSelectAuthorized = publicShareViewModel.canDownloadFiles
            currentFolder = publicShareViewModel.fileClicked
            mainViewModel.setCurrentFolder(currentFolder)
        }
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
                else -> displayFile(file, mainViewModel, fileAdapter)
            }
        }
    }

    private fun populateFileList(files: List<File>, isNewSort: Boolean) {
        val mainApp = requireContext().applicationContext as MainApplication
        fileAdapter.newImageLoader = mainApp.newImageLoader(ImageLoaderType.PublicShared)
        fileAdapter.setFiles(files, isFileListResetNeeded = isNewSort)
        fileAdapter.isComplete = true
        showLoadingTimer.cancel()
        binding.swipeRefreshLayout.isRefreshing = false

        changeNoFilesLayoutVisibility(files.isEmpty(), changeControlsVisibility = true, ignoreOffline = false)
    }

    private fun onBackPressed() {
        publicShareViewModel.cancelDownload()
        if (folderId == publicShareViewModel.rootSharedFile.value?.id || folderId == ROOT_SHARED_FILE_ID) {
            requireActivity().finishAndRemoveTask()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun observeFiles() {
        publicShareViewModel.childrenLiveData.observe(viewLifecycleOwner) { (files, shouldUpdate, isNewSort) ->
            if (shouldUpdate) populateFileList(files, isNewSort)
        }
    }

    private fun observeRootFile() {
        publicShareViewModel.rootSharedFile.observe(viewLifecycleOwner) { file ->
            publicShareViewModel.fileClicked = file
            if (file?.isFolder() == true) {
                openFolder(file)
            } else {
                multiSelectManager.isMultiSelectAuthorized = false
                publicShareViewModel.setSingleRootFile(file)
            }
        }
    }

    private fun observeBookmarkAction() {
        viewLifecycleOwner.lifecycleScope.launch {
            publicShareViewModel.fetchCacheFileForActionResult.collect { (cacheFile, action) ->
                if (action == DownloadAction.OPEN_BOOKMARK) executeOpenBookmarkAction(cacheFile)
            }
        }
    }

    private fun observeFilesImportation() {
        publicShareViewModel.importPublicShareResult.observe(viewLifecycleOwner) { (apiError, destinationPath) ->
            if (apiError == null) {
                showSnackbar(
                    title = R.string.publicShareImportationInProgress,
                    anchor = importButton,
                    actionButtonTitle = R.string.buttonSeeFolder,
                    onActionClicked = {
                        PublicShareUtils.launchDeeplink(requireActivity(), destinationPath, shouldFinish = false)
                    }
                )
            } else {
                showSnackbar(apiError, anchor = importButton)
            }
        }
    }

    private fun openFolder(folder: File) {
        openFolder(
            navigationType = FileListNavigationType.Folder(folder),
            shouldHideBottomNavigation = true,
            shouldShowSmallFab = false,
            fileListViewModel = fileListViewModel,
        )
    }

    private fun openBookmark(file: File) {
        publicShareViewModel.fetchCacheFileForAction(
            file = file,
            action = DownloadAction.OPEN_BOOKMARK,
            navigateToDownloadDialog = {
                Dispatchers.Main {
                    safeNavigate(
                        resId = R.id.previewDownloadProgressDialog,
                        args = PreviewDownloadProgressDialogArgs(file.name).toBundle(),
                    )
                }
            },
        )
    }

    private fun executeOpenBookmarkAction(cacheFile: IOFile?) = lifecycleScope.launch {
        runCatching {
            val uri = FileProvider.getUriForFile(requireContext(), getString(R.string.FILE_AUTHORITY), cacheFile!!)
            with(requireContext()) {
                trackPublicShareActionEvent(MatomoName.OpenBookmark)
                openBookmarkIntent(cacheFile.name, uri)
            }
        }.onFailure { exception ->
            exception.printStackTrace()
            showSnackbar(title = R.string.errorGetBookmarkURL, anchor = importButton)
        }
    }

    private fun downloadAllFiles() {
        // RootSharedFile can either be a folder or a single file
        trackPublicShareActionEvent(MatomoName.DownloadAllFiles)
        publicShareViewModel.rootSharedFile.value?.let { file -> requireContext().downloadFile(downloadPermissions, file) }
    }

    private fun onDriveAndFolderSelected(data: Intent?) {
        val destinationDriveId = data?.getIntExtra(DESTINATION_DRIVE_ID_KEY, PUBLIC_SHARE_DEFAULT_ID) ?: PUBLIC_SHARE_DEFAULT_ID
        val destinationFolderId = data?.getIntExtra(DESTINATION_FOLDER_ID_KEY, PUBLIC_SHARE_DEFAULT_ID) ?: PUBLIC_SHARE_DEFAULT_ID

        if (data == null || destinationDriveId == PUBLIC_SHARE_DEFAULT_ID || destinationFolderId == PUBLIC_SHARE_DEFAULT_ID) {
            showSnackbar(RCore.string.anErrorHasOccurred, anchor = importButton)
        } else {
            val rootSharedFileId = publicShareViewModel.rootSharedFile.value?.id
            val fileIds = multiSelectManager.selectedItemsIds.toList().ifEmpty {
                if (folderId == rootSharedFileId || publicShareViewModel.fileId == rootSharedFileId) {
                    emptyList()
                } else {
                    listOf(folderId)
                }
            }
            closeMultiSelect()
            publicShareViewModel.importFilesToDrive(
                destinationDriveId = destinationDriveId,
                destinationFolderId = destinationFolderId,
                fileIds = fileIds,
                exceptedFileIds = multiSelectManager.exceptedItemsIds,
            )
        }
    }

    private fun setMainButton() {
        importButton.setOnClickListener {
            if (AccountUtils.currentDriveId == -1) {
                trackPublicShareActionEvent(MatomoName.CreateAccountAd)
                safeNavigate(PublicShareListFragmentDirections.actionPublicShareListFragmentToObtainKDriveAdBottomSheet())
            } else {
                trackPublicShareActionEvent(MatomoName.BulkSaveToKDrive)
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
        const val PUBLIC_SHARE_DEFAULT_ID = -1
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            with(publicShareViewModel) {
                val emptyFilesResult = PublicShareFilesResult(files = emptyList(), shouldUpdate = false, isNewSort = false)
                childrenLiveData.value = emptyFilesResult
                cancelDownload()

                if (folderId == ROOT_SHARED_FILE_ID || rootSharedFile.value == null) {
                    downloadPublicShareRootFile()
                } else {
                    getFiles(folderId, fileListViewModel.sortType, isNewSort)
                }
            }
        }
    }
}
