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
package com.infomaniak.drive.ui.fileList.preview

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.MainViewModel
import io.sentry.Sentry

open class PreviewFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val previewViewModel: PreviewViewModel by viewModels()
    protected val previewSliderViewModel: PreviewSliderViewModel by viewModels(ownerProducer = ::requireParentFragment)

    protected lateinit var file: File

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (previewViewModel.currentFile == null) {

            arguments?.let {
                val fileId = it.getInt(FILE_ID_TAG)
                if (fileId > 0) previewViewModel.currentFile = getCurrentFile(fileId)
            }
        }

        previewViewModel.currentFile?.let { file = it }
            ?: lifecycleScope.launchWhenResumed { findNavController().popBackStack() }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun getCurrentFile(fileId: Int): File? = runCatching {
        FileController.getFileById(fileId, previewSliderViewModel.userDrive) ?: mainViewModel.currentPreviewFileList[fileId]
    }.getOrElse { exception ->
        exception.printStackTrace()
        Sentry.withScope { scope ->
            val backStackEntry = findNavController().currentBackStackEntry
            val previousName = findNavController().previousBackStackEntry?.destination?.displayName
            scope.setExtra("destination", "${backStackEntry?.destination?.displayName}")
            scope.setExtra("destination lifecycle", "${backStackEntry?.lifecycle?.currentState}")
            scope.setExtra("previous", previousName ?: "")
            scope.setExtra("exception", exception.stackTraceToString())
            Sentry.captureException(exception)
        }
        null
    }

    protected fun noCurrentFile() = previewViewModel.currentFile == null

    companion object {
        const val FILE_ID_TAG = "file_id_tag"
    }
}
