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
package com.infomaniak.drive.ui.fileList

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.DialogDownloadProgressBinding
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.setBackNavigationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Response

class DownloadProgressDialog : DialogFragment() {

    private val binding: DialogDownloadProgressBinding by lazy { DialogDownloadProgressBinding.inflate(layoutInflater) }
    private val navigationArgs: DownloadProgressDialogArgs by navArgs()
    private val downloadViewModel: DownloadViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = with(navigationArgs) {
        isCancelable = false

        FileController.getFileById(fileId, userDrive)?.let { file ->
            binding.icon.setImageResource(file.getFileType().icon)
            observeDownloadedFile(file)
        }

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(fileName)
            .setView(binding.root)
            .setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    findNavController().popBackStack()
                    true
                } else false
            }
            .create()
    }

    private fun observeDownloadedFile(file: File) = with(navigationArgs) {
        downloadViewModel.downloadFile(requireContext(), file, userDrive).observe(this@DownloadProgressDialog) {
            it?.let { (progress, isComplete) ->
                if (isComplete) {
                    setBackNavigationResult(if (isOpenBookmark) OPEN_BOOKMARK else OPEN_WITH, fileId)
                } else {
                    binding.downloadProgress.progress = progress
                }
            } ?: run {
                showSnackbar(R.string.anErrorHasOccurred)
                findNavController().popBackStack()
            }
        }
    }

    class DownloadViewModel : ViewModel() {

        fun downloadFile(context: Context, file: File, userDrive: UserDrive) = liveData(Dispatchers.IO) {
            val outputFile = file.getStoredFile(context, userDrive)
            if (outputFile == null) {
                emit(null)
                return@liveData
            }
            if (file.isObsoleteOrNotIntact(outputFile)) {
                try {
                    val response = DownloadOfflineFileManager.downloadFileResponse(
                        fileUrl = ApiRoutes.downloadFile(file),
                        downloadInterceptor = DownloadOfflineFileManager.downloadProgressInterceptor { progress ->
                            runBlocking { emit(progress to false) }
                        }
                    )
                    if (response.isSuccessful) {
                        saveData(file, outputFile, response)
                    } else emit(null)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    emit(null)
                }
            } else {
                emit(100 to true)
            }
        }

        private fun LiveDataScope<Pair<Int, IsComplete>?>.saveData(
            file: File,
            outputFile: java.io.File,
            response: Response
        ) {
            if (outputFile.exists()) outputFile.delete()
            DownloadOfflineFileManager.saveRemoteData(TAG, response, outputFile) {
                runBlocking { emit(100 to true) }
            }
            outputFile.setLastModified(file.getLastModifiedInMilliSecond())
        }
    }

    companion object {
        const val OPEN_WITH = "open_with"
        const val OPEN_BOOKMARK = "open_bookmark"
        private const val TAG = "DownloadProgressDialog"
    }
}
