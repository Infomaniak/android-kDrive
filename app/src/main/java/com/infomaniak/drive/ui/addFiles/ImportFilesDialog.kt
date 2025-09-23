/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.FileChunkSizeManager
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.DialogImportFilesBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.SyncUtils.getFileDates
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.getFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.IOException
import java.util.Date

class ImportFilesDialog : DialogFragment() {

    private val dialogBinding by lazy { DialogImportFilesBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navArgs: ImportFilesDialogArgs by navArgs()
    private val importCount by lazy { navArgs.uris.size }

    private var currentImportFile: IOFile? = null
    private var successCount = 0

    private var isMemoryError: Boolean = false
    private var isLowDeviceStorage: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val countMessage = requireContext().resources.getQuantityString(R.plurals.preparingToUpload, importCount, importCount)
        dialogBinding.description.text = countMessage

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setView(dialogBinding.root)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.lifecycleScope.launch { importFiles() }
                }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val errorCount = importCount - successCount

        if (errorCount > 0) {
            val errorMessage = when {
                isLowDeviceStorage -> getString(R.string.errorDeviceStorage)
                isMemoryError -> getString(R.string.uploadOutOfMemoryError)
                else -> {
                    resources.getQuantityString(R.plurals.snackBarUploadError, errorCount, errorCount)
                }
            }

            showSnackbar(errorMessage, showAboveFab = true)
        }

        if (successCount > 0) mainViewModel.refreshActivities.value = true

        currentImportFile?.delete()
    }

    private suspend fun importFiles() {
        val iterator = navArgs.uris.iterator()
        var stopProcessing = false

        while (iterator.hasNext() && !stopProcessing) {
            runCatching {
                initUpload(iterator.next())
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
                when {
                    exception is IOException && exception.message?.contains("ENOSPC|No space left".toRegex()) == true -> {
                        isLowDeviceStorage = true
                        stopProcessing = true
                    }
                    exception is NotEnoughRamException -> {
                        isMemoryError = true
                        stopProcessing = true
                    }
                    else -> SentryLog.e(TAG, "An error has occurred during importFiles", exception)
                }
            }
        }

        if (successCount > 0) appCtx.syncImmediately()
        currentCoroutineContext().ensureActive()
        dismissAllowingStateLoss()
    }

    private suspend fun initUpload(uri: Uri) = withContext(Dispatchers.IO) {
        ensureActive()
        fun captureWithSentry(cursorState: String) {
            // We have cases where importation has failed,
            // but we've added enough information to know the cause.
            SentryLog.e(TAG, "Uri found but cursor is $cursorState") { scope -> scope.setExtra("uri", uri.toString()) }
        }

        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (isLowMemory()) throw NotEnoughRamException()

            ensureActive()
            if (cursor.moveToFirst()) {
                processCursorData(cursor, uri)
            } else {
                captureWithSentry(cursorState = "empty")
            }
        } ?: captureWithSentry(cursorState = "null")
    }

    private suspend fun processCursorData(cursor: Cursor, uri: Uri) = coroutineScope {
        var outputFile: IOFile? = null
        runCatching {
            SentryLog.i(TAG, "processCursorData: uri=$uri")
            val fileName = cursor.getFileName(uri)
            val (fileCreatedAt, fileModifiedAt) = getFileDates(cursor)

            outputFile = getOutputFile(uri, fileModifiedAt)
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
        }.onFailure { exception ->
            if (outputFile?.exists() == true) outputFile.delete()
            throw exception
        }
    }

    private fun isLowMemory(): Boolean {
        val memoryInfo = requireContext().getAvailableMemory()
        return memoryInfo.lowMemory || memoryInfo.availMem < FileChunkSizeManager.CHUNK_MIN_SIZE
    }

    private suspend fun getOutputFile(uri: Uri, fileModifiedAt: Date): IOFile {

        fun captureCannotProcessCopyData() {
            SentryLog.e(TAG, "Uri is valid but data cannot be copied from the import file") { scope ->
                scope.setExtra("uri", uri.toString())
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

    private companion object {
        val TAG: String = ImportFilesDialog::class.java.simpleName
    }
}
