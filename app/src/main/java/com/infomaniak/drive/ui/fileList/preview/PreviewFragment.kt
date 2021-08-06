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
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import io.sentry.Sentry

open class PreviewFragment : Fragment() {

    protected lateinit var file: File
    private val previewViewModel: PreviewViewModel by viewModels()
    protected val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (previewViewModel.currentFile == null) {

            arguments?.let {
                it.getParcelable<File>(FILE_TAG)?.let { file ->
                    previewViewModel.currentFile = getCurrentFile(file)
                }
            }
        }
        previewViewModel.currentFile?.let { file = it } ?: run { findNavController().popBackStack() }
        super.onCreate(savedInstanceState)
    }

    private fun getCurrentFile(file: File) = try {
        FileController.getFileById(file.id, previewSliderViewModel.userDrive) ?: file
    } catch (exception: Exception) {
        exception.printStackTrace()
        Sentry.withScope { scope ->
            val backStackEntry = findNavController().currentBackStackEntry
            val previousName = findNavController().previousBackStackEntry?.destination?.displayName
            scope.setExtra("destination", "${backStackEntry?.destination?.displayName}")
            scope.setExtra("destination lifecycle", "${backStackEntry?.lifecycle?.currentState}")
            scope.setExtra("previous", previousName ?: "")
            scope.setExtra("exception", exception.stackTraceToString())
            Sentry.captureMessage("Get file from preview fragment 🤔")
        }
        file
    }

    protected fun noFileFound() = previewViewModel.currentFile == null

    protected class PreviewViewModel : ViewModel() {
        var currentFile: File? = null
    }

    companion object {
        const val FILE_TAG = "file_id_tag"
    }
}