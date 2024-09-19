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
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.PreviewDownloadProgressDialogArgs
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFHandler
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.FilePresenter.openBookmarkIntent
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke

interface OnPublicShareItemClickListener : FileInfoActionsView.OnItemClickListener {

    val drivePermissions: DrivePermissions
    val publicShareViewModel: PublicShareViewModel
    val previewPDFHandler: PreviewPDFHandler?

    fun initCurrentFile()
    fun onDownloadSuccess()
    fun onDownloadError(@StringRes errorMessage: Int)

    fun observeCacheFileForAction(viewLifecycleOwner: LifecycleOwner) {
        publicShareViewModel.fetchCacheFileForActionResult.observe(viewLifecycleOwner) { (cacheFile, action) ->
            cacheFile?.let { file -> executeDownloadAction(action, file) } ?: onDownloadError(getErrorMessage(action))
        }
    }

    override fun openWith() = startAction(DownloadAction.OPEN_WITH)

    override fun shareFile() = startAction(DownloadAction.SEND_COPY)

    override fun saveToKDrive() = startAction(DownloadAction.SAVE_TO_DRIVE)

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        currentFile?.let { currentContext.downloadFile(drivePermissions, it, ::onDownloadSuccess) }
    }

    override fun printClicked() {
        super.printClicked()
        previewPDFHandler?.printClicked(
            context = currentContext,
            onDefaultCase = { startAction(DownloadAction.PRINT_PDF) },
            onError = { onDownloadError(R.string.errorFileNotFound) },
        )
    }

    private fun startAction(action: DownloadAction) {
        val cacheFileResult = publicShareViewModel.fetchCacheFileForAction(
            file = currentFile,
            navigateToDownloadDialog = ::navigateToDownloadDialog,
        )

        ownerFragment?.viewLifecycleOwner?.let { lifecycleOwner ->
            cacheFileResult.observe(lifecycleOwner) { cacheFile ->
                cacheFile?.let { file -> executeDownloadAction(action, file) } ?: onDownloadError(getErrorMessage(action))
            }
        }
    }

    private suspend fun navigateToDownloadDialog() = Dispatchers.Main {
        currentFile?.let { file ->
            ownerFragment?.safeNavigate(
                resId = R.id.previewDownloadProgressDialog,
                args = PreviewDownloadProgressDialogArgs(file.name).toBundle(),
            )
        }
    }

    private fun executeDownloadAction(downloadAction: DownloadAction, cacheFile: IOFile) = runCatching {
        val uri = FileProvider.getUriForFile(currentContext, currentContext.getString(R.string.FILE_AUTHORITY), cacheFile)

        when (downloadAction) {
            DownloadAction.OPEN_WITH -> {
                currentContext.openWith(uri, currentFile?.getMimeType(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            DownloadAction.SEND_COPY -> currentContext.shareFile { uri }
            DownloadAction.SAVE_TO_DRIVE -> currentContext.saveToKDrive(uri)
            DownloadAction.OPEN_BOOKMARK -> currentContext.openBookmarkIntent(cacheFile.name, uri)
            DownloadAction.PRINT_PDF -> currentContext.printPdf(cacheFile)
        }

        onDownloadSuccess()
    }.onFailure { exception ->
        exception.printStackTrace()
        onDownloadError(getErrorMessage(downloadAction))
    }

    private fun getErrorMessage(downloadAction: DownloadAction): Int {
        return if (downloadAction == DownloadAction.PRINT_PDF) R.string.errorFileNotFound else R.string.errorDownload
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
