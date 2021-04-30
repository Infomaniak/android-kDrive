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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.infomaniak.drive.data.models.File


open class PreviewFragment : Fragment() {

    private lateinit var file: File
    protected lateinit var offlineFile: java.io.File
    protected lateinit var previewViewModel: PreviewViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        previewViewModel = ViewModelProvider(this)[PreviewViewModel::class.java]
        previewViewModel.currentFile = file
        offlineFile = previewViewModel.currentFile.localPath(requireContext(), File.LocalType.OFFLINE)
    }

    fun init(file: File) {
        this.file = file
    }

    protected class PreviewViewModel : ViewModel() {
        lateinit var currentFile: File
    }
}