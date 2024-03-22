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
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.DialogUploadFilesBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.SyncUtils.getFileDates
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.getFileName
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import java.util.Date

class UploadFilesDialog : DialogFragment() {

    private val dialogBinding by lazy { DialogUploadFilesBinding.inflate(LayoutInflater.from(context)) }
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navArgs: UploadFilesDialogArgs by navArgs()
    private val uploadCount by lazy { navArgs.uris.size }

    private var currentFile: IOFile? = null
    private var successCount = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val numberOfUploadMessage = requireContext().resources.getQuantityString(R.plurals.preparingToUpload, uploadCount, uploadCount)
        dialogBinding.description.text = numberOfUploadMessage

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setView(dialogBinding.root)
            .create().also {
                lifecycleScope.launch { uploadFiles() }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val errorCount = uploadCount - successCount

        if (errorCount > 0) displayErrorSnackbar(errorCount)

        if (successCount > 0) mainViewModel.refreshActivities.value = true

        currentFile?.delete()
    }

    private suspend fun uploadFiles() {
        var errorCount = 0
        val uploadFilesJobs = mutableListOf<Job>()
        navArgs.uris.forEach { uri ->
            val uploadJob = getUploadJob(uri, onError = { errorCount++ })
            uploadFilesJobs.add(uploadJob)
        }
        uploadFilesJobs.joinAll()

        if (errorCount > 0) {
            withContext(Dispatchers.Main) {
                displayErrorSnackbar(errorCount)
            }
        }

        lifecycleScope.launch { lifecycle.withResumed { navigateToFileListFragment() } }

        context?.syncImmediately()
    }

    private fun displayErrorSnackbar(errorCount: Int) {
        showSnackbar(
            title = resources.getQuantityString(R.plurals.snackBarUploadError, errorCount, errorCount),
            showAboveFab = true,
        )
    }

    private fun navigateToFileListFragment() {
        with(findNavController()) {
            popBackStack()
            navigate(
                R.id.fileListFragment,
                FileListFragmentArgs(folderId = navArgs.folderId, folderName = navArgs.folderName).toBundle()
            )
        }
    }

    private suspend fun getUploadJob(uri: Uri, onError: () -> Unit) = lifecycleScope.launch {
        runCatching {
            initUpload(uri)
        }.onFailure { exception ->
            exception.printStackTrace()
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("uri", uri.toString())
                Sentry.captureException(exception)
            }
            onError()
        }
    }

    private suspend fun initUpload(uri: Uri) = withContext(Dispatchers.IO) {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val fileName = cursor.getFileName(uri)
                val (fileCreatedAt, fileModifiedAt) = getFileDates(cursor)

                when {
                    isLowMemory() -> withContext(Dispatchers.Main) {
                        showSnackbar(R.string.uploadOutOfMemoryError, showAboveFab = true)
                    }
                    else -> {
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
                        currentFile = null
                    }
                }
            }
        }
    }

    private fun isLowMemory() = with(requireContext().getAvailableMemory()) {
        lowMemory || availMem < UploadTask.chunkSize
    }

    private fun getOutputFile(uri: Uri, fileModifiedAt: Date): IOFile {
        return IOFile(requireContext().uploadFolder, uri.hashCode().toString()).apply {
            if (exists()) delete()
            setLastModified(fileModifiedAt.time)
            createNewFile()
            currentFile = this
            context?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                outputStream().use { inputStream.copyTo(it) }
            }
        }
    }
}
