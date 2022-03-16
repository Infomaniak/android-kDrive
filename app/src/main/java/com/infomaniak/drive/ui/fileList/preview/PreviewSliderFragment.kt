/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.view.ViewCompat.getWindowInsetsController
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackScreen
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntent
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_preview_slider.*
import kotlinx.android.synthetic.main.view_file_info_actions.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var drivePermissions: DrivePermissions
    private lateinit var previewSliderAdapter: PreviewSliderAdapter
    private lateinit var userDrive: UserDrive
    private var hideActions: Boolean = false
    private var showUi = false

    override val ownerFragment = this
    override lateinit var currentFile: File

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (mainViewModel.currentPreviewFileList.isEmpty()) {
            findNavController().popBackStack()
            return null
        }

        if (previewSliderViewModel.currentPreview == null) {
            val isSharedWithMe = arguments?.getBoolean(PREVIEW_IS_SHARED_WITH_ME, false) ?: false
            val driveId = arguments?.getInt(PREVIEW_FILE_DRIVE_ID, 0) ?: 0
            val fileId = arguments?.getInt(PREVIEW_FILE_ID_TAG) ?: savedInstanceState?.getInt(PREVIEW_FILE_ID_TAG)

            userDrive = UserDrive(driveId = driveId, sharedWithMe = isSharedWithMe)
            hideActions = arguments?.getBoolean(PREVIEW_HIDE_ACTIONS, false) ?: false

            currentFile = fileId?.let {
                FileController.getFileById(it, userDrive) ?: mainViewModel.currentPreviewFileList[it]
            } ?: throw Exception("No current preview found")

            previewSliderViewModel.currentPreview = currentFile
            previewSliderViewModel.userDrive = userDrive

        } else {
            previewSliderViewModel.currentPreview?.let { currentFile = it }
            userDrive = previewSliderViewModel.userDrive
        }

        return inflater.inflate(R.layout.fragment_preview_slider, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
        }

        previewSliderAdapter = PreviewSliderAdapter(childFragmentManager, lifecycle)

        viewPager.apply {
            adapter = previewSliderAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    childFragmentManager.findFragmentByTag("f${previewSliderAdapter.getItemId(position)}")?.trackScreen()

                    currentFile = previewSliderAdapter.getFile(position)
                    editButton.isVisible = currentFile.isOnlyOfficePreview()
                    openWithButton.isGone = currentFile.isOnlyOfficePreview()
                    bottomSheetFileInfos.openWith.isVisible = true
                    lifecycleScope.launchWhenResumed {
                        withContext(Dispatchers.Main) { bottomSheetFileInfos.updateCurrentFile(currentFile) }
                    }
                }
            })
        }

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentFile.isOnlyOfficePreview()) openWithButton.isGone = isDownloading
            bottomSheetFileInfos.openWith.isGone = isDownloading
        }

        editButton.setOnClickListener { openOnlyOfficeDocument(currentFile) }
        openWithButton.setOnClickListener { openWithClicked() }
        backButton.setOnClickListener { findNavController().popBackStack() }

        mainViewModel.currentPreviewFileList.let { files ->
            previewSliderAdapter.setFiles(ArrayList(files.values))
            val position = previewSliderAdapter.getPosition(currentFile)
            viewPager.setCurrentItem(position, false)
        }

        configureBottomSheetFileInfo()
    }

    override fun onStart() {
        super.onStart()
        setupTransparentStatusBar()
    }

    override fun onResume() {
        super.onResume()

        with(bottomSheetFileInfos) {
            updateAvailableOfflineItem()
            observeOfflineProgression(this@PreviewSliderFragment) { fileId ->
                previewSliderAdapter.updateFile(fileId) { file -> file.isOffline = true }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        previewSliderViewModel.currentPreview = currentFile
        bottomSheetFileInfos.removeOfflineObservations(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::currentFile.isInitialized) outState.putInt(PREVIEW_FILE_ID_TAG, currentFile.id)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        clearEdgeToEdge()
        super.onStop()
    }

    override fun onDestroy() {
        // Reset current preview file list
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.searchFragment) {
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }

        super.onDestroy()
    }

    private fun clearEdgeToEdge() {
        toggleSystemBar(true)
        requireActivity().window.toggleEdgeToEdge(false)
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_WITH) {
            context?.openWith(currentFile, userDrive)
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            bottomSheetFileInfos.refreshBottomSheetUi(currentFile)
        }
    }

    fun toggleFullscreen() {
        previewSliderParent?.apply {
            val transition = Slide(Gravity.TOP).apply {
                duration = 200
                addTarget(R.id.header)
            }
            TransitionManager.beginDelayedTransition(this, transition)
            header.isVisible = showUi

            toggleBottomSheet(showUi)
            toggleSystemBar(showUi)

            showUi = !showUi
        }
    }

    private fun toggleSystemBar(show: Boolean) {
        getWindowInsetsController(requireActivity().window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val systemBars = WindowInsetsCompat.Type.systemBars()
            if (show) show(systemBars) else hide(systemBars)
        }
    }

    private fun configureBottomSheetFileInfo() {
        activity?.setColorNavigationBar(true)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetFileInfos)
        bottomSheetBehavior.apply {
            isHideable = true
            isDraggable = !hideActions
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (bottomSheetBehavior.state) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            activity?.window?.apply {
                                navigationBarColor =
                                    ContextCompat.getColor(requireContext(), R.color.previewBackgroundTransparent)
                                lightNavigationBar(false)
                            }
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            activity?.setColorStatusBar(true)
                        }
                        else -> {
                            setupTransparentStatusBar()
                            activity?.setColorNavigationBar(true)
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
        }
    }

    private fun setupTransparentStatusBar() {
        activity?.window?.apply {
            statusBarColor = ContextCompat.getColor(requireContext(), R.color.previewBackgroundTransparent)

            lightStatusBar(false)
            toggleEdgeToEdge(true)
        }

        view?.apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
                with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                    header?.setMargin(top = top, right = right, left = left)
                    bottomSheetBehavior.peekHeight = getDefaultPeekHeight() + bottom
                    bottomSheetBehavior.expandedOffset = top
                    bottomSheetFileInfos.setPadding(0, 0, 0, top + bottom)
                }

                windowInsets
            }
        }
    }

    private fun getDefaultPeekHeight(): Int {
        val typedArray = requireContext().theme.obtainStyledAttributes(
            R.style.BottomSheetStyle, intArrayOf(R.attr.behavior_peekHeight)
        )
        val peekHeight = typedArray.getDimensionPixelSize(0, 0)
        typedArray.recycle()
        return peekHeight
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

    override fun copyPublicLink() {
        bottomSheetFileInfos.createPublicCopyLink(onSuccess = { file ->
            previewSliderAdapter.updateFile(currentFile.id) { it.shareLink = file?.shareLink }
            requireActivity().showSnackbar(title = R.string.fileInfoLinkCopiedToClipboard)
            toggleBottomSheet(true)
        }, onError = { translatedError ->
            requireActivity().showSnackbar(title = translatedError)
            toggleBottomSheet(true)
        })
    }

    override fun addFavoritesClicked() {
        super.addFavoritesClicked()
        currentFile.apply {
            val observer: Observer<ApiResponse<Boolean>> = Observer { apiResponse ->
                if (apiResponse.isSuccess()) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                    bottomSheetFileInfos.refreshBottomSheetUi(this)
                } else {
                    requireActivity().showSnackbar(R.string.errorDelete)
                }
                toggleBottomSheet(true)
            }
            if (isFavorite) {
                mainViewModel.deleteFileFromFavorites(this).observe(viewLifecycleOwner, observer)
            } else {
                mainViewModel.addFileToFavorites(this).observe(viewLifecycleOwner, observer)
            }
        }
    }

    private fun toggleBottomSheet(show: Boolean) {
        bottomSheetFileInfos?.scrollToTop()
        bottomSheetBehavior.state = if (show) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun File.showFavoritesResultSnackbar() {
        val id = if (isFavorite) R.string.allFileAddFavoris else R.string.allFileDeleteFavoris
        requireActivity().showSnackbar(getString(id, name))
    }

    override fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile, userDrive)
            previewSliderAdapter.updateFile(currentFile.id) { file -> file.isOffline = false }

            withContext(Dispatchers.Main) {
                currentFile.isOffline = false
                bottomSheetFileInfos.refreshBottomSheetUi(currentFile)
            }
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                if (previewSliderAdapter.deleteFile(currentFile)) {
                    findNavController().popBackStack()
                } else {
                    toggleBottomSheet(true)
                }
                mainViewModel.currentPreviewFileList.remove(currentFile.id)
                requireActivity().showSnackbar(R.string.snackbarLeaveShareConfirmation)
            } else {
                requireActivity().showSnackbar(apiResponse.translatedError)
            }
        }
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        bottomSheetFileInfos.downloadFile(drivePermissions) {
            toggleBottomSheet(true)
        }
    }

    override fun manageCategoriesClicked(fileId: Int) {
        safeNavigate(
            PreviewSliderFragmentDirections.actionPreviewSliderFragmentToSelectCategoriesFragment(
                fileId = fileId,
                categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
            )
        )
    }

    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) {
        val folderId = mainViewModel.currentFolder.value?.id
        mainViewModel.duplicateFile(currentFile, folderId, result).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { file ->
                    mainViewModel.currentPreviewFileList[file.id] = file
                    previewSliderAdapter.addFile(file)
                    requireActivity().showSnackbar(getString(R.string.allFileDuplicate, currentFile.name))
                    toggleBottomSheet(true)
                }
            } else {
                requireActivity().showSnackbar(getString(R.string.errorDuplicate))
                toggleBottomSheet(true)
            }
            onApiResponse()
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        bottomSheetFileInfos.onRenameFile(mainViewModel, newName,
            onSuccess = {
                toggleBottomSheet(true)
                requireActivity().showSnackbar(getString(R.string.allFileRename, currentFile.name))
                onApiResponse()
            }, onError = { translatedError ->
                toggleBottomSheet(true)
                requireActivity().showSnackbar(translatedError)
                onApiResponse()
            })
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                mainViewModel.currentPreviewFileList.remove(currentFile.id)
                if (previewSliderAdapter.deleteFile(currentFile)) {
                    findNavController().popBackStack()
                } else {
                    toggleBottomSheet(true)
                }
                val title = resources.getQuantityString(
                    R.plurals.snackbarMoveTrashConfirmation,
                    1,
                    currentFile.name
                )
                requireActivity().showSnackbar(title)
                mainViewModel.deleteFileFromHome.value = true
            } else {
                requireActivity().showSnackbar(getString(R.string.errorDelete))
            }
        }
    }

    override fun openWithClicked() {
        super.openWithClicked()
        val packageManager = requireContext().packageManager
        if (requireContext().openWithIntent(currentFile, userDrive).resolveActivity(packageManager) == null) {
            requireActivity().showSnackbar(R.string.allActivityNotFoundError)
        } else {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToDownloadProgressDialog(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                    userDrive = userDrive
                )
            )
        }
    }

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentFile, destinationFolder)
            .observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    mainViewModel.refreshActivities.value = true
                    requireActivity().showSnackbar(
                        getString(
                            R.string.allFileMove,
                            currentFile.name,
                            destinationFolder.name
                        )
                    )
                } else {
                    requireActivity().showSnackbar(R.string.errorMove)
                }
            }
    }

    companion object {
        const val PREVIEW_FILE_ID_TAG = "previewFileId"
        const val PREVIEW_FILE_DRIVE_ID = "previewFileDriveId"
        const val PREVIEW_IS_SHARED_WITH_ME = "isSharedWithMe"
        const val PREVIEW_HIDE_ACTIONS = "hideActions"
    }
}
