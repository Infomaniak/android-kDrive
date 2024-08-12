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
package com.infomaniak.drive.ui.fileList

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.DialogDownloadProgressBinding
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseDownloadProgressDialog : DialogFragment() {

    protected val binding: DialogDownloadProgressBinding by lazy { DialogDownloadProgressBinding.inflate(layoutInflater) }
    protected val navigationArgs: DownloadProgressDialogArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        return MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(navigationArgs.fileName)
            .setView(binding.root)
            .setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    findNavController().popBackStack()
                    true
                } else {
                    false
                }
            }
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDownloadedFile()
    }

    abstract fun observeDownloadedFile()

    protected fun setProgress(progress: Int?, onProgressComplete: suspend () -> Unit) {
        progress?.let {
            binding.downloadProgressIndicator.progress = progress
            if (it == DownloadProgressViewModel.PROGRESS_COMPLETE) {
                lifecycleScope.launch {
                    // This delay is here to let the view enough time to render the progress
                    // This make the flow look much smoother for the user even though it's 200ms longer
                    delay(200)
                    onProgressComplete()
                }
            }
        } ?: run {
            findNavController().popBackStack()
            showSnackbar(R.string.anErrorHasOccurred)
        }
    }

    enum class DownloadAction(val value: String) {
        OPEN_BOOKMARK("open_bookmark"),
        OPEN_WITH("open_with"),
        PRINT_PDF("print_pdf"),
        SAVE_TO_DRIVE("save_to_drive"),
        SEND_COPY("send_copy"),
    }

    companion object {
        const val TAG = "DownloadProgressDialog"
    }
}