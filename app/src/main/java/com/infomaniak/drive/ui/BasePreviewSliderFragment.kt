/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.common.extensions.lightStatusBar
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.ui.view.extension.setMargins
import com.infomaniak.core.ui.view.utils.SnackbarUtils.showSnackbar
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFActivity
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFFragment
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFHandler
import com.infomaniak.drive.ui.fileList.preview.PreviewListStore
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderAdapter
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderViewModel
import com.infomaniak.drive.ui.fileList.preview.previewPositionAfterRemoval
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils
import com.infomaniak.drive.ui.fileList.preview.playback.PreviewPlaybackFragment
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.Utils.NO_PARENT_FOLDER_ID
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.openOnlyOfficeDocument
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.drive.utils.toggleSystemBar
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.PreviewHeaderView
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
abstract class BasePreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    protected var _binding: FragmentPreviewSliderBinding? = null
    protected val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    protected val mainViewModel: MainViewModel by activityViewModels()
    protected abstract val previewSliderViewModel: PreviewSliderViewModel
    val sharedPreviewSliderViewModel get() = previewSliderViewModel

    protected abstract val bottomSheetView: View
    protected abstract val bottomSheetBehavior: BottomSheetBehavior<View>

    protected lateinit var previewSliderAdapter: PreviewSliderAdapter
    protected lateinit var userDrive: UserDrive
    protected abstract val isPublicShared: Boolean
    protected abstract val canDownloadFiles: Boolean
    private var isOverlayShown = true

    override val currentContext by lazy { requireContext() }

    private val bottomSheetUpdates = MutableSharedFlow<File>(extraBufferCapacity = 1)

    override lateinit var currentFile: File
    private val previewFiles = mutableListOf<File>()
    private var restoringInitialPage = false
    protected var isPreviewReady = false
        private set
    private val previewReady = MutableStateFlow(false)

    protected abstract suspend fun loadPreviewFiles(): List<File>
    protected abstract fun onPreviewReady()
    protected open fun onPreviewDisplayed() = Unit

    fun getPreviewFile(fileId: Int, driveId: Int?): File? =
        previewFiles.firstOrNull { it.id == fileId && (driveId == null || it.driveId == driveId) }

    suspend fun awaitPreviewFiles() {
        previewReady.first { it }
    }

    protected fun addPreviewFile(file: File): Boolean {
        if (!updateStoredList { it + (file.id to file.driveId) }) return false
        previewFiles.add(file)
        if (previewSliderViewModel.parentFolderId != NO_PARENT_FOLDER_ID) {
            previewSliderViewModel.addedFileIds += file.id
        } else if (previewSliderViewModel.previewListToken == null) {
            previewSliderViewModel.fileIds = previewSliderViewModel.fileIds?.plus(file.id)
            previewSliderViewModel.fileDriveIds = previewSliderViewModel.fileDriveIds?.plus(file.driveId)
        }
        return true
    }

    private fun removePreviewFile(fileId: Int, driveId: Int): Boolean {
        if (!updateStoredList { keys -> keys.filterNot { it == (fileId to driveId) } }) return false
        previewFiles.removeAll { it.id == fileId && it.driveId == driveId }
        if (previewSliderViewModel.parentFolderId != NO_PARENT_FOLDER_ID) {
            previewSliderViewModel.addedFileIds = previewSliderViewModel.addedFileIds.filterNot { it == fileId }.toIntArray()
            previewSliderViewModel.removedFileIds += fileId
        } else if (previewSliderViewModel.previewListToken == null) {
            previewSliderViewModel.removeFile(fileId, driveId)
        }
        return true
    }

    private fun updateStoredList(update: (List<Pair<Int, Int>>) -> List<Pair<Int, Int>>): Boolean {
        val token = previewSliderViewModel.previewListToken ?: return true
        val store = PreviewListStore(requireContext())
        return try {
            store.write(token, update(store.read(token)))
            true
        } catch (exception: Exception) {
            Sentry.captureException(exception)
            store.delete(token)
            showSnackbar(R.string.anErrorHasOccurred)
            findNavController().popBackStack()
            false
        }
    }

    val mediaPositionsMap: MutableMap<Pair<Int, Int>, Long> = mutableMapOf()

    // This is not protected, otherwise it won't build because PublicSharePreviewSliderFragment needs it public for the interface
    // it implements
    val downloadPermissions: DrivePermissions = DrivePermissions(type = DrivePermissions.Type.DownloadingWithDownloadManager)
    open val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {}

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            setPrintVisibility = { isGone ->
                if (_binding == null) return@PreviewPDFHandler
                setPrintButtonVisibility(isGone)
            },
        )
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewSliderViewModel.previewListToken?.let { token ->
            val store = PreviewListStore(requireContext().applicationContext)
            previewSliderViewModel.onPreviewClosed = { store.delete(token) }
        }
        downloadPermissions.registerPermissions(this) { authorized ->
            if (authorized) viewLifecycleOwner.lifecycleScope.launch {
                awaitPreviewFiles()
                downloadFileClicked()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                previewFiles.clear()
                previewFiles.addAll(loadPreviewFiles().filterNot(File::isFolder))
                val selectedId = previewSliderViewModel.currentFileId
                val selectedFile = getPreviewFile(selectedId, if (isPublicShared) null else previewSliderViewModel.currentDriveId)
                if (selectedFile == null) {
                    Sentry.captureMessage("Preview file $selectedId is unavailable")
                    showSnackbar(R.string.errorFileNotFound)
                    findNavController().popBackStack()
                    return@launch
                }
                if (previewSliderViewModel.fileIds?.size?.let { it > previewFiles.size } == true) {
                    showSnackbar(R.string.anErrorHasOccurred)
                }
                currentFile = selectedFile
                if (!isPublicShared) {
                    userDrive = userDrive.copy(driveId = selectedFile.driveId)
                    previewSliderViewModel.userDrive = userDrive
                }
                onPreviewReady()
                setupPreview()
                binding.previewLoading.isGone = true
                isPreviewReady = true
                previewReady.value = true
                onPreviewDisplayed()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Sentry.captureException(exception)
                showSnackbar(R.string.anErrorHasOccurred)
                findNavController().popBackStack()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPreview(): Unit = with(binding) {
        requireActivity().window.lightStatusBar(false)

        setBackActionHandlers()

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
            isPublicShared = isPublicShared,
        )

        previewSliderAdapter.setFiles(ArrayList(previewFiles))
        restoringInitialPage = true
        initViewPager()
        viewPager.setCurrentItem(previewSliderAdapter.getPosition(currentFile), false)
        restoringInitialPage = false
        onPreviewPageSelected(previewSliderAdapter.getPosition(currentFile))

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentFile.isOnlyOfficePreview()) header.toggleOpenWithVisibility(isVisible = !isDownloading)
        }

        binding.header.enableEdgeToEdge(withBottom = false, withRight = false)

        viewLifecycleOwner.lifecycleScope.launch {
            bottomSheetUpdates.collectLatest { file ->
                when (val fileActionBottomSheet = bottomSheetView) {
                    is FileInfoActionsView -> fileActionBottomSheet.updateCurrentFile(file, mainViewModel.hasEligibleDestinationDrives(file))
                    is ExternalFileInfoActionsView -> fileActionBottomSheet.updateWithExternalFile(file)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(State.STARTED) {
                mainViewModel.isNetworkAvailable.collectLatest { isNetworkAvailable ->
                    (bottomSheetView as? FileInfoActionsView)?.updateNetworkAvailability(isNetworkAvailable)
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            binding.header.toggleVisibility(isVisible = false)
            toggleBottomSheet(shouldShow = false)
        }
    }

    override fun onStop() {
        clearEdgeToEdge()
        super.onStop()
    }

    @OptIn(UnstableApi::class)
    override fun onDestroyView() {
        super.onDestroyView()
        isPreviewReady = false
        previewReady.value = false
        _binding?.previewSliderParent?.let(TransitionManager::endTransitions)
        _binding = null
        requireActivity().window.lightStatusBar(!requireActivity().isNightModeEnabled())
    }

    override fun onDestroy() {
        // Release Player
        PlaybackUtils.releasePlayer()

        super.onDestroy()
    }

    private fun initViewPager() {
        binding.viewPager.apply {
            adapter = previewSliderAdapter
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

                @OptIn(UnstableApi::class)
                override fun onPageSelected(position: Int) {
                    if (!restoringInitialPage && position < previewSliderAdapter.itemCount) onPreviewPageSelected(position)
                }
            })
        }
    }

    private fun onPreviewPageSelected(position: Int) {
        val file = previewSliderAdapter.getFile(position)
        currentFile = file
        previewSliderViewModel.currentFileId = file.id
        previewSliderViewModel.currentDriveId = file.driveId
        if (!isPublicShared) userDrive = userDrive.copy(driveId = file.driveId)
        previewSliderViewModel.userDrive = userDrive

        var shouldDisplayPageNumber = false
        val selectedFragment = childFragmentManager.findFragmentByTag("f${previewSliderAdapter.getItemId(position)}")?.apply {
            trackScreen()
            shouldDisplayPageNumber = this is PreviewPDFFragment && tryToUpdatePageCount()
        }

        childFragmentManager.fragments.filterIsInstance<PreviewPlaybackFragment>().forEach { fragment ->
            if (fragment != selectedFragment) fragment.onFragmentUnselected()
        }

        with(binding.header) {
            toggleEditVisibility(isVisible = currentFile.isOnlyOfficePreview())
            setPageNumberVisibility(isVisible = shouldDisplayPageNumber)
            toggleOpenWithVisibility(isVisible = !isPublicShared && !currentFile.isOnlyOfficePreview())
        }
        setPrintButtonVisibility(isGone = !file.isPDF() || !canDownloadFiles)
        (bottomSheetView as? FileInfoActionsView)?.openWith?.isGone = isPublicShared
        bottomSheetUpdates.tryEmit(file)
    }

    protected fun removeFileFromSlider(file: File): Boolean? {
        val selected = currentFile
        val removedPosition = previewSliderAdapter.getPosition(file)
        if (removedPosition < 0) return false
        if (!removePreviewFile(file.id, file.driveId)) return null
        restoringInitialPage = true
        val isEmpty = previewSliderAdapter.deleteFile(file)
        if (!isEmpty) {
            val selectedPosition = previewPositionAfterRemoval(
                removedPosition, previewSliderAdapter.getPosition(selected), previewSliderAdapter.itemCount
            )
            binding.viewPager.setCurrentItem(selectedPosition, false)
            onPreviewPageSelected(selectedPosition)
        }
        restoringInitialPage = false
        return isEmpty
    }

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

    fun isFullscreenOverlayShown(): Boolean = isOverlayShown

    protected fun toggleBottomSheet(shouldShow: Boolean) {
        (bottomSheetView as? FileInfoActionsView)?.scrollToTop()
        bottomSheetBehavior.state = if (shouldShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun clearEdgeToEdge() = with(requireActivity()) {
        toggleSystemBar(true)
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
