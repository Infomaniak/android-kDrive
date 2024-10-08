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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.*
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.PreviewHeaderView
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.setMargins
import com.infomaniak.lib.core.utils.toggleEdgeToEdge
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BasePreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    protected var _binding: FragmentPreviewSliderBinding? = null
    protected val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    protected val mainViewModel: MainViewModel by activityViewModels()
    protected abstract val previewSliderViewModel: PreviewSliderViewModel

    protected abstract val bottomSheetView: View
    protected abstract val bottomSheetBehavior: BottomSheetBehavior<View>

    protected lateinit var previewSliderAdapter: PreviewSliderAdapter
    protected lateinit var userDrive: UserDrive
    protected abstract val isPublicShare: Boolean
    private var isOverlayShown = true

    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File

    // This is not protected, otherwise it won't build because PublicSharePreviewSliderFragment needs it public for the interface
    // it implements
    val drivePermissions: DrivePermissions = DrivePermissions()
    open val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {}

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            context = requireContext(),
            setPrintVisibility = { isGone ->
                if (_binding == null) return@PreviewPDFHandler
                setPrintButtonVisibility(isGone)
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setBackActionHandlers()

        drivePermissions.registerPermissions(this@BasePreviewSliderFragment) { authorized ->
            if (authorized) downloadFileClicked()
        }

        header.apply {
            setupWindowInsetsListener(root, bottomSheetView) { pdfContainer.setMargins(right = it?.right ?: 0) }
            setup(
                onBackClicked = findNavController()::popBackStack,
                onOpenWithClicked = ::openWith,
                onEditClicked = { openOnlyOfficeDocument(currentFile, mainViewModel.hasNetwork) },
            )
        }

        bottomSheetView.apply {
            isVisible = true
            setOnTouchListener { _, _ -> true }
        }

        previewSliderAdapter = PreviewSliderAdapter(
            manager = childFragmentManager,
            lifecycle = lifecycle,
            userDrive = previewSliderViewModel.userDrive,
        )

        viewPager.apply {
            adapter = previewSliderAdapter
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                @OptIn(UnstableApi::class)
                override fun onPageSelected(position: Int) {
                    val selectedFragmentId = previewSliderAdapter.getItemId(position)
                    val selectedFragment = childFragmentManager.findFragmentByTag("f$selectedFragmentId")

                    // Implementation of onFragmentSelected/onFragmentUnselected to handle resume of media to the same position
                    childFragmentManager.fragments
                        .filter {
                            it is PreviewVideoFragment && it != selectedFragment
                        }
                        .forEach { unselectedFragment ->
                            (unselectedFragment as? PreviewVideoFragment)?.onFragmentUnselected()
                        }

                    val file = previewSliderAdapter.getFile(position)
                    currentFile = file
                    previewSliderViewModel.currentPreview = file

                    var shouldDisplayPageNumber = false

                    childFragmentManager.findFragmentByTag("f${previewSliderAdapter.getItemId(position)}")?.apply {
                        this.trackScreen()
                        shouldDisplayPageNumber = this is PreviewPDFFragment && tryToUpdatePageCount()
                    }

                    with(header) {
                        toggleEditVisibility(isVisible = file.isOnlyOfficePreview())
                        setPageNumberVisibility(isVisible = shouldDisplayPageNumber)
                        toggleOpenWithVisibility(isVisible = !isPublicShare && !file.isOnlyOfficePreview())
                    }
                    setPrintButtonVisibility(isGone = !file.isPDF())

                    (bottomSheetView as? FileInfoActionsView)?.openWith?.isGone = isPublicShare
                    bottomSheetUpdates.tryEmit(file)
                }
            })
        }

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentFile.isOnlyOfficePreview()) header.toggleOpenWithVisibility(isVisible = !isDownloading)
        }

        mainViewModel.currentPreviewFileList.let { files ->
            previewSliderAdapter.setFiles(ArrayList(files.values))
            val position = previewSliderAdapter.getPosition(currentFile)
            runCatching {
                viewPager.setCurrentItem(position, false)
            }.onFailure {
                Sentry.withScope { scope ->
                    scope.setExtra("currentFile", "id: ${currentFile.id}")
                    scope.setExtra("files.values", files.values.joinToString { "id: ${it.id}" })
                    Sentry.captureException(it)
                }
                currentFile = files.values.first()
                viewPager.setCurrentItem(0, false)
            }
        }

        binding.header.enableEdgeToEdge(withBottom = false, withRight = false)

        viewLifecycleOwner.lifecycleScope.launch {
            bottomSheetUpdates.collectLatest { file ->
                when (val fileActionBottomSheet = bottomSheetView) {
                    is FileInfoActionsView -> fileActionBottomSheet.updateCurrentFile(file)
                    is ExternalFileInfoActionsView -> fileActionBottomSheet.updateWithExternalFile(file)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setupStatusBarForPreview()
    }

    override fun onPause() {
        super.onPause()
        if (noPreviewList()) return
        previewSliderViewModel.currentPreview = currentFile
    }

    override fun onStop() {
        clearEdgeToEdge()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.previewSliderParent?.let(TransitionManager::endTransitions)
        _binding = null
    }

    override fun onDestroy() {
        // Reset current preview file list
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.searchFragment) {
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }

        super.onDestroy()
    }

    protected fun noPreviewList() = mainViewModel.currentPreviewFileList.isEmpty()

    protected open fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadAction.OPEN_WITH.value) { context?.openWith(currentFile, userDrive) }

        getBackNavigationResult<Int>(DownloadAction.PRINT_PDF.value) {
            requireContext().printPdf(file = currentFile.getCacheFile(requireContext(), userDrive))
        }
    }

    fun toggleFullscreen() = _binding?.let { binding ->
        binding.previewSliderParent.apply {
            isOverlayShown = !isOverlayShown
            binding.header.toggleVisibility(isOverlayShown)
            toggleBottomSheet(shouldShow = isOverlayShown)
            requireActivity().toggleSystemBar(show = isOverlayShown)
        }
    }

    protected fun toggleBottomSheet(shouldShow: Boolean) {
        (bottomSheetView as? FileInfoActionsView)?.scrollToTop()
        bottomSheetBehavior.state = if (shouldShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private val bottomSheetUpdates = MutableSharedFlow<File>(extraBufferCapacity = 1)

    private fun clearEdgeToEdge() = with(requireActivity()) {
        toggleSystemBar(true)
        window.toggleEdgeToEdge(false)
    }

    private fun setPrintButtonVisibility(isGone: Boolean) {
        when (bottomSheetView) {
            is FileInfoActionsView -> (bottomSheetView as FileInfoActionsView).setPrintVisibility(isGone)
            is ExternalFileInfoActionsView -> (bottomSheetView as ExternalFileInfoActionsView).isPrintingHidden(isGone)
        }
    }

    companion object {
        fun Fragment.toggleFullscreen() {
            (parentFragment as? BasePreviewSliderFragment)?.toggleFullscreen()
            (activity as? PreviewPDFActivity)?.toggleFullscreen()
        }

        fun Fragment.openWithClicked() {
            (parentFragment as? BasePreviewSliderFragment)?.openWith()
        }

        fun Fragment.getHeader(): PreviewHeaderView? {
            return (parentFragment as? BasePreviewSliderFragment)?._binding?.header
                ?: (activity as? PreviewPDFActivity)?.binding?.header
        }

        fun Fragment.getPreviewPDFHandler(): PreviewPDFHandler {
            return (parentFragment as? BasePreviewSliderFragment)?.previewPDFHandler
                ?: (activity as? PreviewPDFActivity)!!.previewPDFHandler
        }
    }
}
