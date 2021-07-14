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
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File

open class PreviewFragment : Fragment() {

    protected lateinit var file: File
    private val previewViewModel: PreviewViewModel by viewModels()
    protected val previewSliderViewModel: PreviewSliderFragment.PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (previewViewModel.currentFile == null) {
            arguments?.let {
                val fileId = it.getInt(FILE_ID_TAG)
                previewViewModel.currentFile = FileController.getFileById(fileId, previewSliderViewModel.userDrive)
            }
        }
        previewViewModel.currentFile?.let { file = it } ?: run { findNavController().popBackStack() } // TODO Temporary fix
    }

    protected class PreviewViewModel : ViewModel() {
        var currentFile: File? = null
    }

    companion object {
        const val FILE_ID_TAG = "file_id_tag"
    }
}