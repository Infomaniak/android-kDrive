/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.DialogImportFilesBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ImportFilesDialog : DialogFragment() {

    private val dialogBinding by lazy { DialogImportFilesBinding.inflate(LayoutInflater.from(context)) }
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navArgs: ImportFilesDialogArgs by navArgs()
    private val importCount by lazy { navArgs.importIntent.clipData?.itemCount ?: 1 }

    private var currentImportFile: IoFile? = null
    private var successCount = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val countMessage = requireContext().resources.getQuantityString(R.plurals.fileDetailsInfoFile, importCount, importCount)
        dialogBinding.contentText.text = "${getString(R.string.uploadInProgressTitle)} $countMessage"

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
            val errorMessage = resources.getQuantityString(R.plurals.importFailedDescription, errorCount, errorCount)
            showSnackbar(errorMessage, true)
        }

        if (successCount > 0) mainViewModel.refreshActivities.value = true

        currentImportFile?.delete()
    }

    private suspend fun importFiles() {
        val clipData = navArgs.importIntent.clipData
        val uri = navArgs.importIntent.data
        var errorCount = 0

        try {
            if (clipData != null) {
                val count = clipData.itemCount
                for (i in 0 until count) {
                    runCatching {
                        initUpload(clipData.getItemAt(i).uri)
                    }.onFailure {
                        it.printStackTrace()
                        errorCount++
                    }
                }
            } else if (uri != null) {
                initUpload(uri)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            errorCount++
        } finally {
            if (errorCount > 0) {
                withContext(Dispatchers.Main) {
                    showSnackbar(resources.getQuantityString(R.plurals.snackBarUploadError, errorCount, errorCount), true)
                }
            }
            dismiss()
        }
    }

    private suspend fun initUpload(uri: Uri) = withContext(Dispatchers.IO) {
        requireContext().contentResolver.apply {
            val documentProjection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            query(uri, documentProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val fileName = SyncUtils.getFileName(cursor)
                    val fileModifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val fileModifiedAt = cursor.getLongOrNull(fileModifiedIndex)
                        ?.let(::Date) ?: Date()

                    val memoryInfo = requireContext().getAvailableMemory()
                    val isLowMemory = memoryInfo.lowMemory || memoryInfo.availMem < UploadTask.chunkSize

                    when {
                        isLowMemory -> withContext(Dispatchers.Main) {
                            showSnackbar(R.string.uploadOutOfMemoryError, true)
                        }
                        fileName == null -> withContext(Dispatchers.Main) {
                            showSnackbar(R.string.anErrorHasOccurred, true)
                        }
                        else -> {
                            val outputFile = IoFile(requireContext().uploadFolder, uri.hashCode().toString()).apply {
                                if (exists()) delete()
                                setLastModified(fileModifiedAt.time)
                                createNewFile()
                                currentImportFile = this
                                context?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                                    outputStream().use { inputStream.copyTo(it) }
                                }
                            }

                            if (isActive) {
                                UploadFile(
                                    uri = outputFile.toUri().toString(),
                                    driveId = navArgs.driveId,
                                    fileCreatedAt = fileModifiedAt,
                                    fileModifiedAt = fileModifiedAt,
                                    fileName = fileName,
                                    fileSize = outputFile.length(),
                                    remoteFolder = navArgs.folderId,
                                    type = UploadFile.Type.UPLOAD.name,
                                    userId = AccountUtils.currentUserId,
                                ).store()
                                successCount++
                                currentImportFile = null
                                context?.syncImmediately()
                            }
                        }
                    }
                }
            }
        }
    }
}