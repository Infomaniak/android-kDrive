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

import androidx.annotation.StringRes
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.PreviewDownloadProgressDialogArgs
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFHandler
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OnPublicShareItemClickListener : FileInfoActionsView.OnItemClickListener {

    val drivePermissions: DrivePermissions
    val publicShareViewModel: PublicShareViewModel
    val previewPDFHandler: PreviewPDFHandler?

    fun initCurrentFile()
    fun onDownloadSuccess()
    fun onDownloadError(@StringRes errorMessage: Int)

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
        currentFile?.let { currentContext.downloadFile(drivePermissions, it, ::onDownloadSuccess) }
    }

    override fun printClicked() {
        super.printClicked()
        previewPDFHandler?.printClicked(
            context = currentContext,
            onDefaultCase = { executeActionAndClose(DownloadAction.PRINT_PDF, R.string.errorFileNotFound) },
            onError = { onDownloadError(R.string.errorFileNotFound) },
        )
    }

    private suspend fun navigateToDownloadDialog() = withContext(Dispatchers.Main) {
        currentFile?.let { file ->
            ownerFragment?.safeNavigate(
                resId = R.id.previewDownloadProgressDialog,
                args = PreviewDownloadProgressDialogArgs(file.name).toBundle(),
            )
        }
    }

    private fun executeActionAndClose(action: DownloadAction, @StringRes errorMessageId: Int = R.string.errorDownload) {
        publicShareViewModel.executeDownloadAction(
            activityContext = currentContext,
            downloadAction = action,
            file = currentFile,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
            onDownloadSuccess = ::onDownloadSuccess,
            onDownloadError = { onDownloadError(errorMessageId) },
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
