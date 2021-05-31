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
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.models.File


open class PreviewFragment() : Fragment() {

    private var file: File? = null
    protected lateinit var offlineFile: java.io.File
    protected val previewViewModel: PreviewViewModel by viewModels()

    constructor(file: File) : this() {
        this.file = file
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        file?.let { previewViewModel.currentFile = it }
        offlineFile = previewViewModel.currentFile.localPath(requireContext(), File.LocalType.OFFLINE)
    }

    protected class PreviewViewModel : ViewModel() {
        lateinit var currentFile: File
    }
}