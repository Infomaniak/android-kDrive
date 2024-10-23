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
package com.infomaniak.drive.ui.addFiles

import android.app.Dialog
import android.content.DialogInterface
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.DialogImportFilesBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.getFileDates
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.lib.core.utils.*
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.util.Date

class ImportFilesDialog : DialogFragment() {

    private val dialogBinding by lazy { DialogImportFilesBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navArgs: ImportFilesDialogArgs by navArgs()
    private val importCount by lazy { navArgs.uris.size }

    private var currentImportFile: IOFile? = null
    private var successCount = 0

    private var isMemoryError: Boolean = false
    private var isStorageError: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val countMessage = requireContext().resources.getQuantityString(R.plurals.preparingToUpload, importCount, importCount)
        dialogBinding.description.text = countMessage

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setView(dialogBinding.root)
            .create().also {
                lifecycleScope.launch { importFiles() }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val errorCount = importCount - successCount

        if (errorCount > 0) {
            val errorMessage = if (isMemoryError) {
                getString(R.string.uploadOutOfMemoryError)
            } else if (isStorageError) {
                getString(R.string.errorDeviceStorage)
            } else {
                resources.getQuantityString(R.plurals.snackBarUploadError, errorCount, errorCount)
            }

            showSnackbar(errorMessage, showAboveFab = true)
        }

        if (successCount > 0) mainViewModel.refreshActivities.value = true

        currentImportFile?.delete()
    }

    private suspend fun importFiles() {
        navArgs.uris.forEach { uri ->
            runCatching {
                initUpload(uri)
            }.onFailure { exception ->
                exception.printStackTrace()

                when (exception) {
                    is NotEnoughRamException -> isMemoryError = true
                    is NotEnoughStorageException -> isStorageError = true
                    else -> Sentry.captureException(exception)
                }
            }
        }

        lifecycle.withResumed {
            if (successCount > 0) requireContext().syncImmediately()
            dismiss()
        }
    }

    private suspend fun initUpload(uri: Uri) = withContext(Dispatchers.IO) {
        fun captureWithSentry(cursorState: String) {
            // We have cases where importation has failed,
            // but we've added enough information to know the cause.
            Sentry.withScope { scope ->
                scope.setExtra("uri", uri.toString())
                SentryLog.e(TAG, "Uri found but cursor is $cursorState")
            }
        }

        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (requireContext().isLowMemory()) throw NotEnoughRamException()

            val fileSize = requireContext().getFileSize(cursor, uri)
            if (isLowStorage(fileSize)) throw NotEnoughStorageException()

            if (cursor.moveToFirst()) {
                processCursorData(cursor, uri)
            } else {
                captureWithSentry(cursorState = "empty")
            }
        } ?: captureWithSentry(cursorState = "null")
    }

    private suspend fun CoroutineScope.processCursorData(cursor: Cursor, uri: Uri) {
        SentryLog.i(TAG, "processCursorData: uri=$uri")
        val fileName = cursor.getFileName(uri)
        val (fileCreatedAt, fileModifiedAt) = getFileDates(cursor)

        val outputFile = getOutputFile(uri, fileModifiedAt)
        ensureActive()
        UploadFile(
            uri = outputFile.toUri().toString(),
            driveId = navArgs.driveId,
            fileCreatedAt = fileCreatedAt,
            fileModifiedAt = fileModifiedAt,
            fileName = fileName,
            fileSize = outputFile.length(),
            remoteFolder = navArgs.folderId,
            type = UploadFile.Type.UPLOAD.name,
            userId = AccountUtils.currentUserId,
        ).store()
        successCount++
        currentImportFile = null
    }

    private suspend fun getOutputFile(uri: Uri, fileModifiedAt: Date): IOFile {

        fun captureCannotProcessCopyData() {
            Sentry.withScope { scope ->
                scope.setExtra("uri", uri.toString())
                SentryLog.e(TAG, "Uri is valid but data cannot be copied from the import file")
            }
        }

        val contentResolver = lifecycle.withResumed { requireContext().contentResolver }
        return IOFile(requireContext().uploadFolder, uri.hashCode().toString()).apply {
            if (exists()) delete()
            setLastModified(fileModifiedAt.time)
            createNewFile()
            currentImportFile = this
            contentResolver.openInputStream(uri)?.use { inputStream ->
                outputStream().use { inputStream.copyTo(it) }
            } ?: captureCannotProcessCopyData()
        }
    }

    private class NotEnoughRamException : Exception("Low device memory.")
    private class NotEnoughStorageException : Exception("Low device storage.")

    private companion object {
        val TAG: String = ImportFilesDialog::class.java.simpleName
    }
}
