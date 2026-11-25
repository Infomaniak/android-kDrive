/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.core.ui.showToast
import com.infomaniak.core.ui.view.utils.SnackbarUtils.showSnackbar
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.UploadTask.Companion.LIMIT_EXCEEDED_ERROR_CODE
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.MainViewModel.FileResult
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.ShareLinkViewModel
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.ui.menu.SharedWithMeFragmentArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.navigateToParentFolder
import com.infomaniak.drive.utils.observeCopyToDriveResult
import com.infomaniak.drive.utils.openWith
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.drive.utils.setupBottomSheetFileBehavior
import com.infomaniak.drive.utils.shareText
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class PreviewSliderFragment : BasePreviewSliderFragment(), FileInfoActionsView.OnItemClickListener {

    private val navigationArgs: PreviewSliderFragmentArgs by navArgs()
    private val shareLinkViewModel: ShareLinkViewModel by viewModels()
    override val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    override val bottomSheetView: FileInfoActionsView
        get() = binding.bottomSheetFileInfos
    override val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(binding.bottomSheetFileInfos)

    override val isPublicShared = false
    override val canDownloadFiles = true
    override val ownerFragment = this

    override val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            viewLifecycleOwner.lifecycleScope.launch {
                awaitPreviewFiles()
                onSelectFolderResult(data)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override suspend fun loadPreviewFiles(): List<File> {
        userDrive = UserDrive(
            userId = navigationArgs.userId,
            driveId = navigationArgs.driveId,
            sharedWithMe = navigationArgs.isSharedWithMe,
        )
        previewSliderViewModel.userDrive = userDrive
        val parentFolderId = previewSliderViewModel.parentFolderId
        val removedIds = previewSliderViewModel.removedFileIds.toSet()
        val selectedKey = previewSliderViewModel.currentFileId to
                (previewSliderViewModel.currentDriveId ?: navigationArgs.driveId)
        val sortType = File.SortType.valueOf(navigationArgs.sortType)
        return withContext(Dispatchers.IO) {
            val ids = if (parentFolderId >= 0) {
                previewSliderViewModel.addedFileIds.map { it to navigationArgs.driveId }
            } else {
                previewSliderViewModel.previewListToken?.let { PreviewListStore(requireContext()).read(it) }
                    ?: previewSliderViewModel.fileKeys(navigationArgs.fileId, navigationArgs.driveId)
            }
            val (folderFiles, requestedIds, cachedFiles) = FileController.getRealmInstance(userDrive).use { realm ->
                val folderFiles = if (parentFolderId >= 0) {
                    val folder = realm.where(File::class.java)
                        .equalTo(File::uid.name, "${parentFolderId}_${navigationArgs.driveId}").findFirst()
                    FileController.getLocalSortedFolderFiles(folder, sortType)
                        .filterNot { it.id in removedIds || (it.id to it.driveId) in ids }
                } else {
                    emptyList()
                }
                val idsToLoad = if (
                    parentFolderId >= 0 && selectedKey.first !in removedIds && selectedKey !in ids &&
                    folderFiles.none { (it.id to it.driveId) == selectedKey }
                ) ids + selectedKey else ids
                val cachedFiles = idsToLoad.mapNotNull { (id, driveId) ->
                    realm.where(File::class.java).equalTo(File::uid.name, "${id}_$driveId").findFirst()
                        ?.let { realm.copyFromRealm(it, 1) }
                }.associateBy { it.id to it.driveId }
                Triple(folderFiles, idsToLoad, cachedFiles)
            }
            val missingIds = requestedIds.filterNot { it in cachedFiles }
            val remoteFiles = if (missingIds.isEmpty()) {
                emptyMap()
            } else {
                val client = AccountUtils.getHttpClient(userDrive.userId)
                val semaphore = Semaphore(6)
                coroutineScope {
                    missingIds.map { (id, driveId) ->
                        async {
                            semaphore.withPermit {
                                ApiRepository.getFileDetails(File(id = id, driveId = driveId), client).let { response ->
                                    if (!response.isSuccess()) Sentry.captureMessage("Unable to load preview file $id")
                                    response.data.takeIf { response.isSuccess() && it?.id == id && it.driveId == driveId }
                                }
                            }
                        }
                    }.awaitAll().filterNotNull().associateBy { it.id to it.driveId }
                }
            }
            folderFiles + requestedIds.mapNotNull { cachedFiles[it] ?: remoteFiles[it] }
        }
    }

    override fun onPreviewReady() {
        bottomSheetView.init(
            ownerFragment = this@PreviewSliderFragment,
            mainViewModel = mainViewModel,
            shareLinkViewModel = shareLinkViewModel,
            onItemClickListener = this@PreviewSliderFragment,
            selectFolderResultLauncher = selectFolderResultLauncher,
            isSharedWithMe = userDrive.sharedWithMe,
            hideActions = false,
        )
        bottomSheetView.updateCurrentFile(currentFile, mainViewModel.hasEligibleDestinationDrives(currentFile))

        requireActivity().setupBottomSheetFileBehavior(bottomSheetBehavior, isDraggable = true)

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            bottomSheetView.openWith.isGone = isDownloading
        }

        observeCopyToDriveResult(mainViewModel) { message ->
            showSnackbar(message)
            toggleBottomSheet(shouldShow = true)
        }
    }

    override fun onPreviewDisplayed() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) observeOfflineProgress()
    }

    override fun onResume() {
        super.onResume()

        if (isPreviewReady) observeOfflineProgress()
    }

    private fun observeOfflineProgress() {
        _binding?.bottomSheetFileInfos?.let { fileInfoActionView ->
            val adapterFiles = (0 until previewSliderAdapter.itemCount).map(previewSliderAdapter::getFile)
            refreshPersistedOfflineStates(adapterFiles + currentFile)
            fileInfoActionView.updateCurrentFile(currentFile, mainViewModel.hasEligibleDestinationDrives(currentFile))
            fileInfoActionView.observeOfflineProgression(this@PreviewSliderFragment) { fileId ->
                previewSliderAdapter.updateFile(fileId, currentFile.driveId) { file -> file.isOffline = true }
            }
        }
    }

    private fun refreshPersistedOfflineStates(files: List<File>) {
        FileController.getRealmInstance(userDrive).use { realm ->
            FileController.refreshOfflineStates(realm, files)
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.bottomSheetFileInfos?.removeOfflineObservations(this)
    }

    override fun setBackActionHandlers() {
        super.setBackActionHandlers()

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            FileController.getFileCategories(currentFile.id, userDrive)?.let { categories ->
                if (!currentFile.isManaged) currentFile.categories = categories
                previewSliderAdapter.updateFile(currentFile.id) { file ->
                    if (!file.isManaged) file.categories = categories
                }
                _binding?.bottomSheetFileInfos?.refreshBottomSheetUi(currentFile)
            }
        }
    }

    override fun displayInfoClicked() {
        currentFile.apply {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileDetailsFragment(
                    fileId = id,
                    userDrive = userDrive,
                )
            )
        }
    }

    override fun fileRightsClicked() {
        safeNavigate(PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileShareDetailsFragment(currentFile.id))
    }

    override fun goToFolder() {
        FileController.getParentFileByUid(currentFile.id, userDrive)?.let { folder ->
            if (userDrive.sharedWithMe) {
                safeNavigate(
                    R.id.sharedWithMeFragment,
                    SharedWithMeFragmentArgs(folderId = folder.id, folderName = folder.name, driveId = folder.driveId, userDrive = userDrive)
                        .toBundle(),
                )
            } else {
                navigateToParentFolder(folder.id, mainViewModel, userDrive)
            }
        }
    }

    override fun sharePublicLink(onActionFinished: () -> Unit) {
        super<FileInfoActionsView.OnItemClickListener>.sharePublicLink(onActionFinished)
        binding.bottomSheetFileInfos.createPublicShareLink(
            onSuccess = { shareLinkUrl ->
                context?.shareText(text = shareLinkUrl)
                toggleBottomSheet(shouldShow = true)
                onActionFinished()
            },
            onError = { translatedError ->
                showSnackbar(translatedError)
                toggleBottomSheet(shouldShow = true)
                onActionFinished()
            },
        )
    }

    override fun addFavoritesClicked() {
        super<FileInfoActionsView.OnItemClickListener>.addFavoritesClicked()
        currentFile.apply {
            val observer: Observer<FileResult> = Observer { fileRequest ->
                if (fileRequest.isSuccess) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                    binding.bottomSheetFileInfos.refreshBottomSheetUi(this)
                } else {
                    showSnackbar(R.string.errorDelete)
                }
                toggleBottomSheet(shouldShow = true)
            }
            if (isFavorite) {
                mainViewModel.deleteFileFromFavorites(this).observe(viewLifecycleOwner, observer)
            } else {
                mainViewModel.addFileToFavorites(this).observe(viewLifecycleOwner, observer)
            }
        }
    }

    private fun File.showFavoritesResultSnackbar() {
        val id = if (isFavorite) R.string.allFileAddFavoris else R.string.allFileDeleteFavoris
        showSnackbar(getString(id, name))
    }

    override fun removeOfflineFile(offlineLocalPath: IOFile?, cacheFile: IOFile) {
        val file = currentFile
        if (offlineLocalPath == null) {
            showSnackbar(R.string.errorDelete)
            binding.bottomSheetFileInfos.onOfflineRemovalFinished(file)
            return
        }
        val appContext = requireContext().applicationContext
        mainViewModel.removeOfflineFile(file, offlineLocalPath, cacheFile, userDrive) { success ->
            if (!success) {
                appContext.showToast(title = appContext.getString(R.string.errorDelete))
                if (_binding != null) {
                    binding.bottomSheetFileInfos.onOfflineRemovalFinished(file)
                }
                return@removeOfflineFile
            }
            file.isOffline = false
            mainViewModel.updateOfflineFile.value = file.id
            if (_binding != null) {
                previewSliderAdapter.updateFile(file.id, file.driveId) { it.isOffline = false }
                if (currentFile.id == file.id && currentFile.driveId == file.driveId) currentFile.isOffline = false
                binding.bottomSheetFileInfos.onOfflineRemovalFinished(file)
            }
        }
    }

    override fun downloadFileClicked() {
        super<BasePreviewSliderFragment>.downloadFileClicked()
        currentContext.downloadFile(downloadPermissions, currentFile) { toggleBottomSheet(shouldShow = true) }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        val file = currentFile
        mainViewModel.deleteFile(file, userDrive).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider(file)
                showSnackbar(R.string.snackbarLeaveShareConfirmation)
            } else {
                fileRequest.errorResId?.let { showSnackbar(it) }
            }
        }
    }

    override fun manageCategoriesClicked(fileId: Int) {
        safeNavigate(
            PreviewSliderFragmentDirections.actionPreviewSliderFragmentToSelectCategoriesFragment(
                filesIds = intArrayOf(fileId),
                categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
                userDrive = UserDrive(driveId = currentFile.driveId)
            )
        )
    }

    override fun onCopyFileToDrive(destinationFolder: File) {
        mainViewModel.copyFileToAnotherDrive(currentFile, destinationFolder)
    }

    override fun onDuplicateFile(destinationFolder: File) {
        mainViewModel.duplicateFile(currentFile, destinationFolder.id).observe(viewLifecycleOwner) { fileResult ->
            if (fileResult.isSuccess) {
                (fileResult.data as? File)?.let { file ->
                    if (currentFile.parentId == destinationFolder.id) {
                        if (addPreviewFile(file)) previewSliderAdapter.addFile(file)
                    }

                    showSnackbar(getString(R.string.allFileDuplicate, currentFile.name))
                    toggleBottomSheet(shouldShow = true)
                }
            } else if (fileResult.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                showSnackbar(R.string.errorFilesLimitExceeded)
                toggleBottomSheet(shouldShow = true)
            } else {
                showSnackbar(R.string.errorDuplicate)
                toggleBottomSheet(shouldShow = true)
            }
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        binding.bottomSheetFileInfos.onRenameFile(
            newName = newName,
            onSuccess = {
                toggleBottomSheet(shouldShow = true)
                showSnackbar(getString(R.string.allFileRename, currentFile.name))
                onApiResponse()
            },
            onError = { translatedError ->
                toggleBottomSheet(shouldShow = true)
                showSnackbar(translatedError)
                onApiResponse()
            }
        )
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        val file = currentFile
        mainViewModel.deleteFile(file, userDrive).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider(file)
                showSnackbar(getString(R.string.snackbarMoveTrashConfirmation, file.name))
                mainViewModel.deleteFileFromHome.value = true
                mainViewModel.deleteFilesFromGallery.postValue(
                    mainViewModel.deleteFilesFromGallery.value?.plus(file.id) ?: listOf(file.id)
                )
            } else {
                showSnackbar(R.string.errorDelete)
            }
        }
    }

    override fun openWith() {
        context?.openWith(ownerFragment = this, currentFile = currentFile, userDrive = userDrive) {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToDownloadProgressDialog(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                    userDrive = userDrive,
                )
            )
        }
    }

    override fun onMoveFile(destinationFolder: File, isSharedWithMe: Boolean) {
        val file = currentFile
        mainViewModel.moveFile(file, destinationFolder)
            .observe(viewLifecycleOwner) { fileRequest ->
                if (fileRequest.isSuccess) {
                    // Because if we are on the favorite view we do not want to remove it for example
                    if (findNavController().previousBackStackEntry?.destination?.id == R.id.fileListFragment) removeFileInSlider(file)
                    mainViewModel.refreshActivities.value = true
                    showSnackbar(getString(R.string.allFileMove, file.name, destinationFolder.name))
                } else {
                    val messageRes = fileRequest.errorResId ?: R.string.errorMove
                    showSnackbar(messageRes)
                }
            }
    }

    private fun removeFileInSlider(file: File) {
        when (removeFileFromSlider(file)) {
            true -> findNavController().popBackStack()
            false -> toggleBottomSheet(shouldShow = true)
            null -> Unit
        }
    }

    override fun shareFile() = Unit
    override fun saveToKDrive() = Unit
    override fun onCacheAddedToOffline() {
        previewSliderAdapter.updateFile(currentFile.id, currentFile.driveId) { it.isOffline = true }
    }

    override fun printClicked() {
        trackFileActionEvent(MatomoName.PrintPdf)
        previewPDFHandler.printClicked(
            context = requireContext(),
            onDefaultCase = {
                requireContext().printPdf {
                    safeNavigate(
                        PreviewSliderFragmentDirections.actionPreviewSliderFragmentToDownloadProgressDialog(
                            fileId = currentFile.id,
                            fileName = currentFile.name,
                            userDrive = userDrive,
                            action = DownloadAction.PRINT_PDF,
                        ),
                    )
                }
            },
            onError = { showSnackbar(R.string.errorFileNotFound) },
        )
    }
}
