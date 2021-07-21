/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.view.View
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
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.dialog_download_progress.*
import kotlinx.android.synthetic.main.dialog_download_progress.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Response

class DownloadProgressDialog : DialogFragment() {

    private val navigationArgs: DownloadProgressDialogArgs by navArgs()
    private val downloadViewModel: DownloadViewModel by viewModels()

    private lateinit var dialogView: View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = View.inflate(context, R.layout.dialog_download_progress, null)

        isCancelable = false
        FileController.getFileById(navigationArgs.fileID, navigationArgs.userDrive)?.let { file ->
            dialogView.icon.setImageResource(file.getFileType().icon)
            downloadViewModel.downloadFile(requireContext(), file, navigationArgs.userDrive).observe(this) {
                it?.let { (progress, isComplete) ->
                    if (isComplete) {
                        setBackNavigationResult(OPEN_WITH, true)
                    } else {
                        downloadProgress.progress = progress
                    }
                } ?: run {
                    requireActivity().showSnackbar(R.string.anErrorHasOccurred)
                    findNavController().popBackStack()
                }
            }
        }

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(navigationArgs.fileName)
            .setView(dialogView)
            .setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    findNavController().popBackStack()
                    true
                } else false
            }
            .create()
    }

    override fun getView() = dialogView

    class DownloadViewModel : ViewModel() {

        fun downloadFile(context: Context, file: File, userDrive: UserDrive) = liveData(Dispatchers.IO) {
            val outputFile =
                if (file.isOffline) file.getOfflineFile(context, userDrive.userId)
                else file.getCacheFile(context, userDrive)
            if (outputFile == null) {
                emit(null)
                return@liveData
            }
            if (outputFile.length() != file.size || file.isObsolete(outputFile)) {
                try {
                    val response = DownloadWorker.downloadFileResponse(ApiRoutes.downloadFile(file)) { progress ->
                        runBlocking { emit(progress to false) }
                    }
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

        @Throws(Exception::class)
        private fun LiveDataScope<Pair<Int, IsComplete>?>.saveData(
            file: File,
            outputFile: java.io.File,
            response: Response
        ) {
            if (outputFile.exists()) outputFile.delete()
            DownloadWorker.saveRemoteData(response, outputFile) {
                runBlocking { emit(100 to true) }
            }
            outputFile.setLastModified(file.getLastModifiedInMilliSecond())
        }
    }

    companion object {
        const val OPEN_WITH = "open_with"
    }

}