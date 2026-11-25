/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewOthersBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.MainViewModel
import io.sentry.Sentry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

open class PreviewFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val previewViewModel: PreviewViewModel by viewModels()
    private val standalonePreviewSliderViewModel: PreviewSliderViewModel by viewModels(ownerProducer = ::requireParentFragment)
    protected val previewSliderViewModel: PreviewSliderViewModel
        get() = (parentFragment as? BasePreviewSliderFragment)?.sharedPreviewSliderViewModel ?: standalonePreviewSliderViewModel

    protected val navigationArgs by lazy { arguments?.let(PreviewFragmentArgs.Companion::fromBundle) }

    protected open val noNetworkBinding: FragmentPreviewOthersBinding? get() = null

    protected lateinit var file: File
    private var isFileReady = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFileReady = false
        val parent = parentFragment as? BasePreviewSliderFragment
        val fileId = navigationArgs?.fileId
        if (parent != null && fileId != null && fileId > 0 && parent.getPreviewFile(fileId) == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                parent.awaitPreviewFiles()
                initPreviewFile()
            }
        } else {
            initPreviewFile()
        }
    }

    private fun initPreviewFile() {
        if (previewViewModel.currentFile == null) {
            navigationArgs?.let {
                previewSliderViewModel.userDrive = it.userDrive ?: UserDrive()
                if (it.fileId > 0) previewViewModel.currentFile = getCurrentFile(it.fileId)
            }
        }

        val currentFile = previewViewModel.currentFile
        if (currentFile != null) {
            file = currentFile
            isFileReady = true
            observeNetworkToReloadPreview()
        } else if (navigationArgs?.fileId?.let { it > 0 } == true) {
            viewLifecycleOwner.lifecycleScope.launchWhenResumed { findNavController().popBackStack() }
            return
        }
        onPreviewFileReady()
    }

    protected open fun onPreviewFileReady() = Unit

    override fun onDestroyView() {
        isFileReady = false
        super.onDestroyView()
    }

    private fun observeNetworkToReloadPreview() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isNetworkAvailable
                    .filter { it }
                    .collectLatest {
                        if (!noCurrentFile()) reloadPreviewIfNeeded()
                    }
            }
        }
    }

    private fun getCurrentFile(fileId: Int): File? = runCatching {
        (parentFragment as? BasePreviewSliderFragment)?.getPreviewFile(fileId)
            ?: FileController.getFileByUidOrId(fileId, previewSliderViewModel.userDrive)
    }.getOrElse { exception ->
        exception.printStackTrace()
        Sentry.captureException(exception) { scope ->
            val previousName = findNavController().previousBackStackEntry?.destination?.displayName
            val backStackEntry = findNavController().currentBackStackEntry
            scope.setExtra("destination", "${backStackEntry?.destination?.displayName}")
            scope.setExtra("destination lifecycle", "${backStackEntry?.lifecycle?.currentState}")
            scope.setExtra("previous", previousName ?: "")
            scope.setExtra("exception", exception.stackTraceToString())
        }
        null
    }

    protected open fun reloadPreviewIfNeeded() = Unit

    protected fun noCurrentFile() = !isFileReady
    protected fun hasInitializedFile() = ::file.isInitialized

    protected fun isFileUnavailableOffline(): Boolean = !mainViewModel.hasNetwork && !canDisplayFileOffline()

    protected open fun canDisplayFileOffline(): Boolean {
        val context = requireContext()
        val userDrive = previewSliderViewModel.userDrive
        val storedFile = if (file.isOnlyOfficePreview()) {
            file.getConvertedPdfCache(context, userDrive)
        } else {
            file.getStoredFile(context, userDrive)
        }
        return (storedFile?.length() ?: 0L) > 0L
    }

    protected fun isOfflineCopyIntact(): Boolean {
        return file.getOfflineFile(requireContext(), previewSliderViewModel.userDrive.userId)
            ?.let { file.isOfflineAndIntact(it) } == true
    }

    protected open fun showNoNetwork() {
        noNetworkBinding?.apply {
            fileName.text = file.name
            previewDescription.setText(R.string.allNoNetwork)
            previewDescription.isVisible = true
            bigOpenWithButton.isGone = true
            root.isVisible = true
        }
    }

    protected open fun hideNoNetwork() {
        noNetworkBinding?.root?.isGone = true
    }
}
