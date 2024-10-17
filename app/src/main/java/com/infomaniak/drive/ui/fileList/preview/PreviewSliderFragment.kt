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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.MatomoDrive.ACTION_PRINT_PDF_NAME
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
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
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.whenResultIsOk
import kotlinx.coroutines.launch

class PreviewSliderFragment : BasePreviewSliderFragment(), FileInfoActionsView.OnItemClickListener {

    private val navigationArgs: PreviewSliderFragmentArgs by navArgs()
    private val shareLinkViewModel: ShareLinkViewModel by viewModels()
    override val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    override val bottomSheetView: FileInfoActionsView
        get() = binding.bottomSheetFileInfos
    override val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(binding.bottomSheetFileInfos)

    override val isPublicShare = false
    override val ownerFragment = this

    override val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (noPreviewList()) {
            findNavController().popBackStack()
            return null
        }

        if (previewSliderViewModel.currentPreview == null) {
            userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = navigationArgs.isSharedWithMe)

            currentFile = FileController.getFileById(navigationArgs.fileId, userDrive)
                ?: mainViewModel.currentPreviewFileList[navigationArgs.fileId] ?: throw Exception("No current preview found")

            previewSliderViewModel.currentPreview = currentFile
            previewSliderViewModel.userDrive = userDrive
        } else {
            previewSliderViewModel.currentPreview?.let { currentFile = it }
            userDrive = previewSliderViewModel.userDrive
        }

        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().setupBottomSheetFileBehavior(bottomSheetBehavior, !navigationArgs.hideActions)

        bottomSheetView.apply {
            init(
                ownerFragment = this@PreviewSliderFragment,
                mainViewModel = mainViewModel,
                shareLinkViewModel = shareLinkViewModel,
                onItemClickListener = this@PreviewSliderFragment,
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)

            previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
                openWith.isGone = isDownloading
            }
        }
    }

    override fun onResume() {
        super.onResume()

        _binding?.bottomSheetFileInfos?.let { fileInfoActionView ->
            fileInfoActionView.updateAvailableOfflineItem()
            fileInfoActionView.observeOfflineProgression(this@PreviewSliderFragment) { fileId ->
                previewSliderAdapter.updateFile(fileId) { file -> file.isOffline = true }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.bottomSheetFileInfos?.removeOfflineObservations(this)
    }

    override fun setBackActionHandlers() {
        super.setBackActionHandlers()

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            _binding?.bottomSheetFileInfos?.refreshBottomSheetUi(currentFile)
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
        FileController.getParentFile(currentFile.id)?.let { folder -> navigateToParentFolder(folder.id, mainViewModel) }
    }

    override fun sharePublicLink(onActionFinished: () -> Unit) {
        super<FileInfoActionsView.OnItemClickListener>.sharePublicLink(onActionFinished)
        binding.bottomSheetFileInfos.createPublicShareLink(
            onSuccess = { shareLinkUrl ->
                context?.shareText(shareLinkUrl)
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

    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile, userDrive)
            previewSliderAdapter.updateFile(currentFile.id) { file -> file.isOffline = false }

            currentFile.isOffline = false
            binding.bottomSheetFileInfos.refreshBottomSheetUi(currentFile)
        }
    }

    override fun downloadFileClicked() {
        super<BasePreviewSliderFragment>.downloadFileClicked()
        currentContext.downloadFile(drivePermissions, currentFile) { toggleBottomSheet(shouldShow = true) }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider()
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

    override fun onDuplicateFile(destinationFolder: File) {
        mainViewModel.duplicateFile(currentFile, destinationFolder.id).observe(viewLifecycleOwner) { fileResult ->
            if (fileResult.isSuccess) {
                (fileResult.data as? File)?.let { file ->
                    if (currentFile.parentId == destinationFolder.id) {
                        mainViewModel.currentPreviewFileList[file.id] = file
                        previewSliderAdapter.addFile(file)
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
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider()
                showSnackbar(getString(R.string.snackbarMoveTrashConfirmation, currentFile.name))
                mainViewModel.deleteFileFromHome.value = true
                mainViewModel.deleteFilesFromGallery.postValue(
                    mainViewModel.deleteFilesFromGallery.value?.plus(currentFile.id) ?: listOf(currentFile.id)
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

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentFile, destinationFolder)
            .observe(viewLifecycleOwner) { fileRequest ->
                if (fileRequest.isSuccess) {
                    // Because if we are on the favorite view we do not want to remove it for example
                    if (findNavController().previousBackStackEntry?.destination?.id == R.id.fileListFragment) removeFileInSlider()
                    mainViewModel.refreshActivities.value = true
                    showSnackbar(getString(R.string.allFileMove, currentFile.name, destinationFolder.name))
                } else {
                    val messageRes = if (fileRequest.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                        R.string.errorFilesLimitExceeded
                    } else {
                        R.string.errorMove
                    }

                    showSnackbar(messageRes)
                }
            }
    }

    private fun removeFileInSlider() {
        mainViewModel.currentPreviewFileList.remove(currentFile.id)
        if (previewSliderAdapter.deleteFile(currentFile)) {
            findNavController().popBackStack()
        } else {
            toggleBottomSheet(shouldShow = true)
        }
    }

    override fun shareFile() = Unit
    override fun saveToKDrive() = Unit
    override fun onCacheAddedToOffline() = Unit

    override fun printClicked() {
        requireContext().trackFileActionEvent(ACTION_PRINT_PDF_NAME)
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
