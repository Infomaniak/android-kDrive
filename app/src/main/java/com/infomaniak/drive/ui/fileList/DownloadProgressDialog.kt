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
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.setBackNavigationResult

class DownloadProgressDialog : BaseDownloadProgressDialog() {

    private val downloadProgressViewModel: DownloadProgressViewModel by viewModels()
    override val navigationArgs: DownloadProgressDialogArgs by navArgs()

    override val dialogTitle get() = navigationArgs.fileName

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = with(navigationArgs) {
        downloadProgressViewModel.getLocalFile(fileId, userDrive)
        super.onCreateDialog(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeLocalFile()
    }

    override fun observeDownloadedFile() = with(navigationArgs) {
        downloadProgressViewModel.downloadProgressLiveData.observe(viewLifecycleOwner) { progress ->
            setProgress(progress) { setBackNavigationResult(action.value, fileId) }
        }
    }

    private fun observeLocalFile() {
        downloadProgressViewModel.localFile.observe(viewLifecycleOwner) { file ->
            binding.icon.setImageResource(file.getFileType().icon)
            downloadProgressViewModel.downloadFile(requireContext(), file, navigationArgs.userDrive)
        }
    }
}
