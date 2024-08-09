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
package com.infomaniak.drive.ui.fileList

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.DialogDownloadProgressBinding
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.setBackNavigationResult

class DownloadProgressDialog : DialogFragment() {

    private val binding: DialogDownloadProgressBinding by lazy { DialogDownloadProgressBinding.inflate(layoutInflater) }
    private val navigationArgs: DownloadProgressDialogArgs by navArgs()
    private val downloadProgressViewModel: DownloadProgressViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = with(navigationArgs) {
        isCancelable = false

        FileController.getFileById(fileId, userDrive)?.let { file ->
            binding.icon.setImageResource(file.getFileType().icon)
            observeDownloadedFile()
            startDownloadFile(file, userDrive)
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

    private fun startDownloadFile(file: File, userDrive: UserDrive) {
        downloadProgressViewModel.downloadFile(requireContext(), file, userDrive)
    }

    private fun observeDownloadedFile() = with(navigationArgs) {
        downloadProgressViewModel.downloadProgressLiveData.observe(this@DownloadProgressDialog) { progress ->
            progress?.let {
                if (it == DownloadProgressViewModel.PROGRESS_COMPLETE) {
                    setBackNavigationResult(action.value, fileId)
                } else {
                    binding.downloadProgressIndicator.progress = progress
                }
            } ?: run {
                showSnackbar(R.string.anErrorHasOccurred)
                findNavController().popBackStack()
            }
        }
    }

    companion object {
        private const val TAG = "DownloadProgressDialog"
    }
}
