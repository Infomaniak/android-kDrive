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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentBottomSheetPublicShareFileActionsBinding
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.PreviewDownloadProgressDialogArgs
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PublicShareFileActionsBottomSheetDialog : BottomSheetDialogFragment(), FileInfoActionsView.OnItemClickListener {

    private var binding: FragmentBottomSheetPublicShareFileActionsBinding by safeBinding()
    private val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override val ownerFragment = this
    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File

    private val mainButton by lazy { (requireActivity() as PublicShareActivity).getMainButton() }
    private val drivePermissions = DrivePermissions()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetPublicShareFileActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initBottomSheet()

        drivePermissions.registerPermissions(this@PublicShareFileActionsBottomSheetDialog) { authorized ->
            if (authorized) downloadFileClicked()
        }
    }

    private fun setCurrentFile() {
        currentFile = publicShareViewModel.fileClicked ?: run {
            findNavController().popBackStack()
            return
        }
    }

    private fun initBottomSheet() = with(binding.publicShareFileActionsView) {
        setCurrentFile()
        updateWithExternalFile(currentFile)
        initOnClickListener(onItemClickListener = this@PublicShareFileActionsBottomSheetDialog)
        isPrintingHidden(isGone = currentFile.isPDF())
    }

    override fun openWith() {
        executeActionAndClose(DownloadAction.OPEN_WITH)
    }

    override fun shareFile() {
        executeActionAndClose(DownloadAction.SEND_COPY)
    }

    override fun saveToKDrive() {
        executeActionAndClose(DownloadAction.SAVE_TO_DRIVE)
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        requireContext().downloadFile(drivePermissions, currentFile, findNavController()::popBackStack)
    }

    override fun printClicked() {
        super.printClicked()
        executeActionAndClose(DownloadAction.PRINT_PDF, R.string.errorFileNotFound)
    }

    private suspend fun navigateToDownloadDialog() = withContext(Dispatchers.Main) {
        safeNavigate(
            R.id.previewDownloadProgressDialog,
            PreviewDownloadProgressDialogArgs(
                fileId = currentFile.id,
                fileName = currentFile.name,
                userDrive = UserDrive(),
                action = DownloadAction.SAVE_TO_DRIVE,
            ).toBundle(),
        )
    }

    private fun executeActionAndClose(action: DownloadAction, @StringRes errorMessageId: Int = R.string.errorDownload) {
        publicShareViewModel.executeDownloadAction(
            activityContext = requireContext(),
            downloadAction = action,
            file = currentFile,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
            onDownloadError = {
                showSnackbar(errorMessageId, anchor = mainButton)
                findNavController().popBackStack()
            },
        )
    }

    override fun displayInfoClicked() = Unit
    override fun fileRightsClicked() = Unit
    override fun goToFolder() = Unit
    override fun manageCategoriesClicked(fileId: Int) = Unit
    override fun onCacheAddedToOffline() = Unit
    override fun onDeleteFile(onApiResponse: () -> Unit) = Unit
    override fun onLeaveShare(onApiResponse: () -> Unit) = Unit
    override fun onDuplicateFile(destinationFolder: File) = Unit
    override fun onMoveFile(destinationFolder: File) = Unit
    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit
    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit
}
