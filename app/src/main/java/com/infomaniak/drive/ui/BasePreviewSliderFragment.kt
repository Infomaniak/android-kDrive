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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.*
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BasePreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    protected var _binding: FragmentPreviewSliderBinding? = null
    protected val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    protected val mainViewModel: MainViewModel by activityViewModels()
    protected abstract val previewSliderViewModel: PreviewSliderViewModel

    protected val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(binding.bottomSheetFileInfos)

    protected lateinit var drivePermissions: DrivePermissions
    protected lateinit var previewSliderAdapter: PreviewSliderAdapter
    protected lateinit var userDrive: UserDrive
    protected var isOverlayShown = true
    protected abstract val isFileShare: Boolean

    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File

    open val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {}

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            context = requireContext(),
            setPrintVisibility = { _binding?.bottomSheetFileInfos?.setPrintVisibility(it) },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setBackActionHandlers()

        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@BasePreviewSliderFragment) { authorized -> if (authorized) downloadFileClicked() }
        }

        bottomSheetFileInfos.apply {
            init(
                ownerFragment = this@BasePreviewSliderFragment,
                mainViewModel = mainViewModel,
                onItemClickListener = this@BasePreviewSliderFragment,
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)
            setPrintVisibility(isGone = !currentFile.isPDF())
            setOnTouchListener { _, _ -> true }
        }

        previewSliderAdapter = PreviewSliderAdapter(childFragmentManager, lifecycle)

        viewPager.apply {
            adapter = previewSliderAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    childFragmentManager.findFragmentByTag("f${previewSliderAdapter.getItemId(position)}")?.trackScreen()

                    currentFile = previewSliderAdapter.getFile(position)
                    with(header) {
                        toggleEditVisibility(isVisible = currentFile.isOnlyOfficePreview())
                        toggleOpenWithVisibility(isVisible = !currentFile.isOnlyOfficePreview())
                    }
                    bottomSheetFileInfos.openWith.isVisible = true
                    bottomSheetFileInfos.setPrintVisibility(isGone = !currentFile.isPDF())

                    lifecycleScope.launch(Dispatchers.Main) {
                        repeatOnLifecycle(State.RESUMED) { bottomSheetFileInfos.updateCurrentFile(currentFile) }
                    }
                }
            })
        }

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentFile.isOnlyOfficePreview()) header.toggleOpenWithVisibility(isVisible = !isDownloading)
            bottomSheetFileInfos.openWith.isGone = isDownloading
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

        header.setupWindowInsetsListener(rootView = root, bottomSheetView = bottomSheetFileInfos) {
            pdfContainer.setMargins(right = it?.right ?: 0)
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
        _binding?.bottomSheetFileInfos?.removeOfflineObservations(this)
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

    protected fun clearEdgeToEdge() = with(requireActivity()) {
        toggleSystemBar(true)
        window.toggleEdgeToEdge(false)
    }

    protected fun noPreviewList() = mainViewModel.currentPreviewFileList.isEmpty()

    protected open fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadAction.OPEN_WITH.value) { context?.openWith(currentFile, userDrive) }

        getBackNavigationResult<Int>(DownloadAction.PRINT_PDF.value) {
            requireContext().printPdf(file = currentFile.getCacheFile(requireContext()))
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
        binding.bottomSheetFileInfos.scrollToTop()
        bottomSheetBehavior.state = if (shouldShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun printClicked() {
        super.printClicked()
        previewPDFHandler.printClicked(
            context = requireContext(),
            onDefaultCase = {
                requireContext().printPdf {
                    safeNavigate(
                        PreviewSliderFragmentDirections.actionPreviewSliderFragmentToDownloadProgressDialog(
                            fileId = currentFile.id,
                            fileName = currentFile.name,
                            userDrive = userDrive,
                            action = DownloadAction.PRINT_PDF,
                        ),
                    )
                }
            },
            onError = { showSnackbar(R.string.errorFileNotFound) },
        )
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        binding.bottomSheetFileInfos.downloadFile(drivePermissions) {
            toggleBottomSheet(shouldShow = true)
        }
    }

    companion object {
        fun Fragment.toggleFullscreen() {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
            (activity as? PreviewPDFActivity)?.toggleFullscreen()
        }

        fun Fragment.openWithClicked() {
            (parentFragment as? PreviewSliderFragment)?.openWith()
        }
    }
}
