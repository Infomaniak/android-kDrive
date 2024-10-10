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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.OptIn
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask.Companion.LIMIT_EXCEEDED_ERROR_CODE
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MainViewModel.FileResult
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.ui.fileList.preview.playback.PreviewPlaybackFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.PreviewHeaderView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    private var _binding: FragmentPreviewSliderBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: PreviewSliderFragmentArgs by navArgs()
    private val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    private val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(binding.bottomSheetFileInfos)

    private lateinit var drivePermissions: DrivePermissions
    private lateinit var previewSliderAdapter: PreviewSliderAdapter
    private lateinit var userDrive: UserDrive
    private var isOverlayShown = true

    override val ownerFragment = this
    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            context = requireContext(),
            setPrintVisibility = { _binding?.bottomSheetFileInfos?.setPrintVisibility(it) },
        )
    }

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (noPreviewList()) {
            findNavController().popBackStack()
            return null
        }

        if (previewSliderViewModel.currentPreview == null) {
            userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = navigationArgs.isSharedWithMe)

            currentFile = FileController.getFileById(navigationArgs.fileId, userDrive)
                ?: mainViewModel.currentPreviewFileList[navigationArgs.fileId] ?: throw Exception("No current preview found")

            previewSliderViewModel.currentPreview = currentFile
            previewSliderViewModel.userDrive = userDrive

        } else {
            previewSliderViewModel.currentPreview?.let { currentFile = it }
            userDrive = previewSliderViewModel.userDrive
        }

        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setBackActionHandlers()

        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@PreviewSliderFragment) { authorized -> if (authorized) downloadFileClicked() }
        }

        bottomSheetFileInfos.apply {
            init(
                ownerFragment = this@PreviewSliderFragment,
                mainViewModel = mainViewModel,
                onItemClickListener = this@PreviewSliderFragment,
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)
            setOnTouchListener { _, _ -> true }
            setPrintVisibility(isGone = !currentFile.isPDF())
        }

        previewSliderAdapter = PreviewSliderAdapter(childFragmentManager, lifecycle)

        viewPager.apply {
            adapter = previewSliderAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

                @OptIn(UnstableApi::class)
                override fun onPageSelected(position: Int) {
                    val selectedFragment = childFragmentManager.findFragmentByTag("f${previewSliderAdapter.getItemId(position)}")

                    selectedFragment?.trackScreen()

                    // Implementation of onFragmentUnselected to handle resume of media to the same position, only
                    // for PreviewVideoFragment.
                    childFragmentManager.fragments.filter {
                        it is PreviewPlaybackFragment && it != selectedFragment
                    }.forEach { unselectedFragment ->
                        (unselectedFragment as? PreviewPlaybackFragment)?.onFragmentUnselected()
                    }

                    currentFile = previewSliderAdapter.getFile(position)
                    with(header) {
                        toggleEditVisibility(isVisible = currentFile.isOnlyOfficePreview())
                        toggleOpenWithVisibility(isVisible = !currentFile.isOnlyOfficePreview())
                    }

                    // Update of BottomSheet
                    bottomSheetFileInfos.openWith.isVisible = true
                    lifecycleScope.launchWhenResumed {
                        withContext(Dispatchers.Main) { bottomSheetFileInfos.updateCurrentFile(currentFile) }
                    }
                    bottomSheetFileInfos.setPrintVisibility(isGone = !currentFile.isPDF())
                }
            })
        }

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentFile.isOnlyOfficePreview()) binding.header.toggleOpenWithVisibility(isVisible = !isDownloading)
            bottomSheetFileInfos.openWith.isGone = isDownloading
        }

        binding.header.setup(
            onBackClicked = { findNavController().popBackStack() },
            onOpenWithClicked = { openWith() },
            onEditClicked = { openOnlyOfficeDocument(currentFile, mainViewModel.hasNetwork) },
        )

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

        requireActivity().setupBottomSheetFileBehavior(bottomSheetBehavior, !navigationArgs.hideActions)
        binding.header.setupWindowInsetsListener(
            rootView = root,
            bottomSheetView = binding.bottomSheetFileInfos,
        ) {
            binding.pdfContainer.setMargins(right = it?.right ?: 0)
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setupStatusBarForPreview()
    }

    override fun onResume() {
        super.onResume()

        _binding?.bottomSheetFileInfos?.let { fileInfoActionView ->
            fileInfoActionView.updateAvailableOfflineItem()
            fileInfoActionView.observeOfflineProgression(this@PreviewSliderFragment) { fileId ->
                previewSliderAdapter.updateFile(fileId) { file -> file.isOffline = true }
            }
        }
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

    private fun clearEdgeToEdge() = with(requireActivity()) {
        toggleSystemBar(true)
        window.toggleEdgeToEdge(false)
    }

    private fun noPreviewList() = mainViewModel.currentPreviewFileList.isEmpty()

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadAction.OPEN_WITH.value) { context?.openWith(currentFile, userDrive) }

        getBackNavigationResult<Int>(DownloadAction.PRINT_PDF.value) {
            requireContext().printPdf(file = currentFile.getCacheFile(requireContext()))
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            _binding?.bottomSheetFileInfos?.refreshBottomSheetUi(currentFile)
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

    override fun displayInfoClicked() {
        currentFile.apply {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileDetailsFragment(
                    fileId = id,
                    userDrive = userDrive,
                )
            )
        }
    }

    override fun fileRightsClicked() {
        safeNavigate(PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileShareDetailsFragment(currentFile.id))
    }

    override fun goToFolder() {
        FileController.getParentFile(currentFile.id)?.let { folder -> navigateToParentFolder(folder.id, mainViewModel) }
    }

    override fun sharePublicLink(onActionFinished: () -> Unit) {
        super.sharePublicLink(onActionFinished)
        binding.bottomSheetFileInfos.createPublicShareLink(
            onSuccess = { shareLinkUrl ->
                context?.shareText(shareLinkUrl)
                toggleBottomSheet(shouldShow = true)
                onActionFinished()
            },
            onError = { translatedError ->
                showSnackbar(translatedError)
                toggleBottomSheet(shouldShow = true)
                onActionFinished()
            },
        )
    }

    override fun addFavoritesClicked() {
        super.addFavoritesClicked()
        currentFile.apply {
            val observer: Observer<FileResult> = Observer { fileRequest ->
                if (fileRequest.isSuccess) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                    binding.bottomSheetFileInfos.refreshBottomSheetUi(this)
                } else {
                    showSnackbar(R.string.errorDelete)
                }
                toggleBottomSheet(shouldShow = true)
            }
            if (isFavorite) {
                mainViewModel.deleteFileFromFavorites(this).observe(viewLifecycleOwner, observer)
            } else {
                mainViewModel.addFileToFavorites(this).observe(viewLifecycleOwner, observer)
            }
        }
    }

    private fun toggleBottomSheet(shouldShow: Boolean) {
        binding.bottomSheetFileInfos.scrollToTop()
        bottomSheetBehavior.state = if (shouldShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun File.showFavoritesResultSnackbar() {
        val id = if (isFavorite) R.string.allFileAddFavoris else R.string.allFileDeleteFavoris
        showSnackbar(getString(id, name))
    }

    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile, userDrive)
            previewSliderAdapter.updateFile(currentFile.id) { file -> file.isOffline = false }

            currentFile.isOffline = false
            binding.bottomSheetFileInfos.refreshBottomSheetUi(currentFile)
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider()
                showSnackbar(R.string.snackbarLeaveShareConfirmation)
            } else {
                fileRequest.errorResId?.let { showSnackbar(it) }
            }
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

    override fun manageCategoriesClicked(fileId: Int) {
        safeNavigate(
            PreviewSliderFragmentDirections.actionPreviewSliderFragmentToSelectCategoriesFragment(
                filesIds = intArrayOf(fileId),
                categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
                userDrive = UserDrive(driveId = currentFile.driveId)
            )
        )
    }

    override fun onDuplicateFile(destinationFolder: File) {
        mainViewModel.duplicateFile(currentFile, destinationFolder.id).observe(viewLifecycleOwner) { fileResult ->
            if (fileResult.isSuccess) {
                (fileResult.data as? File)?.let { file ->
                    if (currentFile.parentId == destinationFolder.id) {
                        mainViewModel.currentPreviewFileList[file.id] = file
                        previewSliderAdapter.addFile(file)
                    }

                    showSnackbar(getString(R.string.allFileDuplicate, currentFile.name))
                    toggleBottomSheet(shouldShow = true)
                }
            } else if (fileResult.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                showSnackbar(R.string.errorFilesLimitExceeded)
                toggleBottomSheet(shouldShow = true)
            } else {
                showSnackbar(R.string.errorDuplicate)
                toggleBottomSheet(shouldShow = true)
            }
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        binding.bottomSheetFileInfos.onRenameFile(mainViewModel, newName,
            onSuccess = {
                toggleBottomSheet(shouldShow = true)
                showSnackbar(getString(R.string.allFileRename, currentFile.name))
                onApiResponse()
            }, onError = { translatedError ->
                toggleBottomSheet(shouldShow = true)
                showSnackbar(translatedError)
                onApiResponse()
            })
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { fileRequest ->
            onApiResponse()
            if (fileRequest.isSuccess) {
                removeFileInSlider()
                showSnackbar(getString(R.string.snackbarMoveTrashConfirmation, currentFile.name))
                mainViewModel.deleteFileFromHome.value = true
            } else {
                showSnackbar(R.string.errorDelete)
            }
        }
    }

    override fun openWith() {
        context?.openWith(ownerFragment = this, currentFile = currentFile) {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToDownloadProgressDialog(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                    userDrive = userDrive,
                )
            )
        }
    }

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentFile, destinationFolder)
            .observe(viewLifecycleOwner) { fileRequest ->
                if (fileRequest.isSuccess) {
                    // Because if we are on the favorite view we do not want to remove it for example
                    if (findNavController().previousBackStackEntry?.destination?.id == R.id.fileListFragment) removeFileInSlider()
                    mainViewModel.refreshActivities.value = true
                    showSnackbar(getString(R.string.allFileMove, currentFile.name, destinationFolder.name))
                } else {
                    val messageRes = if (fileRequest.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                        R.string.errorFilesLimitExceeded
                    } else {
                        R.string.errorMove
                    }

                    showSnackbar(messageRes)
                }
            }
    }

    private fun removeFileInSlider() {
        mainViewModel.currentPreviewFileList.remove(currentFile.id)
        if (previewSliderAdapter.deleteFile(currentFile)) {
            findNavController().popBackStack()
        } else {
            toggleBottomSheet(shouldShow = true)
        }
    }

    override fun shareFile() = Unit
    override fun saveToKDrive() = Unit
    override fun onCacheAddedToOffline() = Unit

    companion object {

        private fun Fragment.getHeader(): PreviewHeaderView? {
            return (parentFragment as? PreviewSliderFragment)?._binding?.header
                ?: (activity as? PreviewPDFActivity)?.binding?.header
        }

        fun Fragment.toggleFullscreen() {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
            (activity as? PreviewPDFActivity)?.toggleFullscreen()
        }

        fun Fragment.openWithClicked() {
            (parentFragment as? PreviewSliderFragment)?.openWith()
        }

        //region PDF Preview
        fun Fragment.setPageNumberChipVisibility(isVisible: Boolean) {
            getHeader()?.setPageNumberVisibility(isVisible)
        }

        fun Fragment.setPageNumber(currentPage: Int, totalPage: Int) {
            getHeader()?.setPageNumberValue(currentPage, totalPage)
        }

        fun Fragment.getPreviewPDFHandler(): PreviewPDFHandler {
            return (parentFragment as? PreviewSliderFragment)?.previewPDFHandler
                ?: (activity as? PreviewPDFActivity)!!.previewPDFHandler
        }
        //endregion
    }
}
