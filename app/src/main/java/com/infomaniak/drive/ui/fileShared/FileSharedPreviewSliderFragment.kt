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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderViewModel
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.setupBottomSheetFileBehavior
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileSharedPreviewSliderFragment : BasePreviewSliderFragment(), FileInfoActionsView.OnItemClickListener {

    private val navigationArgs: FileSharedPreviewSliderFragmentArgs by navArgs()
    override val previewSliderViewModel: PreviewSliderViewModel by activityViewModels()

    override val bottomSheetView: ExternalFileInfoActionsView
        get() = binding.fileSharedBottomSheetFileActions
    override val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(bottomSheetView)

    override val isFileShare = true
    override val ownerFragment = this

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (noPreviewList()) {
            findNavController().popBackStack()
            return null
        }

        currentFile = mainViewModel.currentPreviewFileList[navigationArgs.fileId] ?: throw Exception("No current preview found")

        previewSliderViewModel.currentPreview = currentFile

        if (previewSliderViewModel.currentPreview == null) {
            userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = true)
            previewSliderViewModel.userDrive = userDrive
        } else {
            userDrive = previewSliderViewModel.userDrive
        }

        previewSliderViewModel.shareLinkUuid = navigationArgs.shareLinkUuid

        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initBottomSheet()
    }

    private fun initBottomSheet() = with(bottomSheetView) {
        requireActivity().setupBottomSheetFileBehavior(bottomSheetBehavior, isDraggable = true, isFitToContents = true)
        updateWithExternalFile(currentFile)
        initOnClickListener(onItemClickListener = this@FileSharedPreviewSliderFragment)
    }

    private suspend fun navigateToDownloadDialog() = withContext(Dispatchers.Main) {
        safeNavigate(
            FileSharedPreviewSliderFragmentDirections.actionFileSharedPreviewSliderFragmentToDownloadProgressDialog(
                fileId = currentFile.id,
                fileName = currentFile.name,
                userDrive = userDrive,
                action = DownloadAction.SAVE_TO_DRIVE,
            )
        )
    }

    override fun displayInfoClicked() = Unit
    override fun fileRightsClicked() = Unit
    override fun goToFolder() = Unit
    override fun manageCategoriesClicked(fileId: Int) = Unit

    override fun openWith() {
        previewSliderViewModel.executeDownloadAction(
            activityContext = requireContext(),
            downloadAction = DownloadAction.OPEN_WITH,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
            onDownloadError = { showSnackbar(R.string.errorDownload, anchor = bottomSheetView) },
        )
    }

    override fun shareFile() {
        previewSliderViewModel.executeDownloadAction(
            activityContext = requireContext(),
            downloadAction = DownloadAction.SEND_COPY,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
            onDownloadError = { showSnackbar(R.string.errorDownload, anchor = bottomSheetView) },
        )
    }

    override fun saveToKDrive() {
        previewSliderViewModel.executeDownloadAction(
            activityContext = requireContext(),
            downloadAction = DownloadAction.SAVE_TO_DRIVE,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
            onDownloadError = { showSnackbar(R.string.errorDownload, anchor = bottomSheetView) },
        )
    }

    override fun downloadFileClicked() {
        super<BasePreviewSliderFragment>.downloadFileClicked()
        binding.bottomSheetFileInfos.downloadFile(drivePermissions, currentFile) { toggleBottomSheet(shouldShow = true) }
    }

    override fun printClicked() {
        super<BasePreviewSliderFragment>.printClicked()
        previewPDFHandler.printClicked(
            context = requireContext(),
            onDefaultCase = {
                previewSliderViewModel.executeDownloadAction(
                    activityContext = requireContext(),
                    downloadAction = DownloadAction.PRINT_PDF,
                    navigateToDownloadDialog = ::navigateToDownloadDialog,
                    onDownloadError = { showSnackbar(R.string.errorFileNotFound, anchor = bottomSheetView) },
                )
            },
            onError = { showSnackbar(R.string.errorFileNotFound) },
        )
    }

    override fun onCacheAddedToOffline() = Unit
    override fun onDeleteFile(onApiResponse: () -> Unit) = Unit
    override fun onLeaveShare(onApiResponse: () -> Unit) = Unit
    override fun onDuplicateFile(destinationFolder: File) = Unit
    override fun onMoveFile(destinationFolder: File) = Unit
    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit
    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit
}
