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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.ColorFolderBottomSheetDialog
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.*
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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var currentPreviewFile: File
    private lateinit var drivePermissions: DrivePermissions
    private lateinit var previewSliderAdapter: PreviewSliderAdapter
    private val mainViewModel: MainViewModel by activityViewModels()
    private val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)
    private var hideActions: Boolean = false

    private lateinit var userDrive: UserDrive

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

            currentPreviewFile = fileId?.let {
                FileController.getFileById(it, userDrive) ?: mainViewModel.currentPreviewFileList[it]
            } ?: throw Exception("No current preview found")

            previewSliderViewModel.currentPreview = currentPreviewFile
            previewSliderViewModel.userDrive = userDrive

        } else {
            previewSliderViewModel.currentPreview?.let { currentPreviewFile = it }
            userDrive = previewSliderViewModel.userDrive
        }

        return inflater.inflate(R.layout.fragment_preview_slider, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setBackActionHandlers()

        drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { authorized -> if (authorized) downloadFileClicked() }

        previewSliderAdapter = PreviewSliderAdapter(childFragmentManager, lifecycle)
        viewPager.adapter = previewSliderAdapter
        viewPager.offscreenPageLimit = 1

        bottomSheetFileInfos.init(this, mainViewModel, this, userDrive.sharedWithMe)
        bottomSheetFileInfos.updateCurrentFile(currentPreviewFile)
        bottomSheetFileInfos.setOnTouchListener { _, _ -> true }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPreviewFile = previewSliderAdapter.getFile(position)
                editButton.isVisible = currentPreviewFile.isOnlyOfficePreview()
                openWithButton.isGone = currentPreviewFile.isOnlyOfficePreview()
                bottomSheetFileInfos.openWith.isVisible = true

                lifecycleScope.launchWhenResumed {
                    withContext(Dispatchers.Main) {
                        bottomSheetFileInfos.updateCurrentFile(currentPreviewFile)
                    }
                }
            }
        })

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (!currentPreviewFile.isOnlyOfficePreview()) openWithButton.isGone = isDownloading
            bottomSheetFileInfos.openWith.isGone = isDownloading
        }

        editButton.setOnClickListener { openOnlyOfficeDocument(currentPreviewFile) }
        openWithButton.setOnClickListener { openWithClicked() }
        backButton.setOnClickListener { findNavController().popBackStack() }

        mainViewModel.currentPreviewFileList.let { files ->
            previewSliderAdapter.setFiles(ArrayList(files.values))
            val position = previewSliderAdapter.getPosition(currentPreviewFile)
            viewPager.setCurrentItem(position, false)
        }

        configureBottomSheetFileInfo()
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_WITH) {
            context?.openWith(currentPreviewFile, userDrive)
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            bottomSheetFileInfos.refreshBottomSheetUi(currentPreviewFile)
        }

        getBackNavigationResult<String>(ColorFolderBottomSheetDialog.COLOR_FOLDER_NAV_KEY) {
            updateFolderColor(it)
        }
    }

    private fun updateFolderColor(color: String) {
        if (isResumed) {
            mainViewModel.updateFolderColor(currentPreviewFile, color).observe(viewLifecycleOwner) { apiResponse ->
                findNavController().popBackStack()
                if (!apiResponse.isSuccess()) requireActivity().showSnackbar(apiResponse.translatedError)
            }
        }
    }

    private var showUi = false
    fun toggleFullscreen() {
        previewSliderParent?.apply {
            val transition = Slide(Gravity.TOP)
            transition.duration = 200
            transition.addTarget(R.id.header)
            TransitionManager.beginDelayedTransition(this, transition)
            header.isVisible = showUi
            toggleBottomSheet(showUi)
            showUi = !showUi
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
                            activity?.window?.navigationBarColor =
                                ContextCompat.getColor(requireContext(), R.color.previewBackground)
                            activity?.window?.lightNavigationBar(false)
                        }
                        else -> {
                            activity?.setColorNavigationBar(true)
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onSelectFolderResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.statusBarColor = ContextCompat.getColor(requireContext(), R.color.previewBackground)
        activity?.window?.lightStatusBar(false)
        bottomSheetFileInfos.updateAvailableOfflineItem()
        bottomSheetFileInfos.observeOfflineProgression(this) { fileId ->
            previewSliderAdapter.updateFile(fileId) { file ->
                file.isOffline = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        previewSliderViewModel.currentPreview = currentPreviewFile
        bottomSheetFileInfos.removeOfflineObservations(this)
    }

    override fun onDestroy() {
        // Reset current preview file list
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.searchFragment) {
            mainViewModel.currentPreviewFileList = LinkedHashMap()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::currentPreviewFile.isInitialized) outState.putInt(PREVIEW_FILE_ID_TAG, currentPreviewFile.id)
        super.onSaveInstanceState(outState)
    }

    override fun displayInfoClicked() {
        currentPreviewFile.apply {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileDetailsFragment(
                    fileId = id,
                    userDrive = userDrive
                )
            )
        }
    }

    override fun fileRightsClicked() {
        safeNavigate(PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileShareDetailsFragment(currentPreviewFile.id))
    }

    override fun copyPublicLink() {
        bottomSheetFileInfos.createPublicCopyLink(onSuccess = { file ->
            previewSliderAdapter.updateFile(currentPreviewFile.id) { it.shareLink = file?.shareLink }
            requireActivity().showSnackbar(title = R.string.fileInfoLinkCopiedToClipboard)
            toggleBottomSheet(true)
        }, onError = { translatedError ->
            requireActivity().showSnackbar(title = translatedError)
            toggleBottomSheet(true)
        })
    }

    override fun addFavoritesClicked() {
        currentPreviewFile.apply {
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

    private fun toggleBottomSheet(show: Boolean? = null) {
        val mustShow = show ?: (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN)
        bottomSheetFileInfos?.scrollToTop()
        bottomSheetBehavior.state = if (mustShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun File.showFavoritesResultSnackbar() {
        if (isFavorite) {
            requireActivity().showSnackbar(getString(R.string.allFileAddFavoris, name))
        } else {
            requireActivity().showSnackbar(getString(R.string.allFileDeleteFavoris, name))
        }
    }

    override fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentPreviewFile, offlineLocalPath, cacheFile, userDrive)
            previewSliderAdapter.updateFile(currentPreviewFile.id) { file -> file.isOffline = false }

            withContext(Dispatchers.Main) {
                currentPreviewFile.isOffline = false
                bottomSheetFileInfos.refreshBottomSheetUi(currentPreviewFile)
            }
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentPreviewFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                if (previewSliderAdapter.deleteFile(currentPreviewFile)) {
                    findNavController().popBackStack()
                } else {
                    toggleBottomSheet(true)
                }
                mainViewModel.currentPreviewFileList.remove(currentPreviewFile.id)
                requireActivity().showSnackbar(R.string.snackbarLeaveShareConfirmation)
            } else {
                requireActivity().showSnackbar(apiResponse.translatedError)
            }
        }
    }

    override fun downloadFileClicked() {
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

    override fun colorFolderClicked(color: String) {
        if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {
            safeNavigate(R.id.colorFolderUpgradeBottomSheetDialog)
        } else {
            safeNavigate(PreviewSliderFragmentDirections.actionPreviewSliderFragmentToColorFolderBottomSheetDialog(color))
        }
    }

    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) {
        val folderID = mainViewModel.currentFolder.value?.id
        mainViewModel.duplicateFile(currentPreviewFile, folderID, result).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { file ->
                    mainViewModel.currentPreviewFileList[file.id] = file
                    previewSliderAdapter.addFile(file)
                    requireActivity().showSnackbar(getString(R.string.allFileDuplicate, currentPreviewFile.name))
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
                requireActivity().showSnackbar(getString(R.string.allFileRename, currentPreviewFile.name))
                onApiResponse()
            }, onError = { translatedError ->
                toggleBottomSheet(true)
                requireActivity().showSnackbar(translatedError)
                onApiResponse()
            })
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentPreviewFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                mainViewModel.currentPreviewFileList.remove(currentPreviewFile.id)
                if (previewSliderAdapter.deleteFile(currentPreviewFile)) {
                    findNavController().popBackStack()
                } else {
                    toggleBottomSheet(true)
                }
                val title = resources.getQuantityString(
                    R.plurals.snackbarMoveTrashConfirmation,
                    1,
                    currentPreviewFile.name
                )
                requireActivity().showSnackbar(title)
                mainViewModel.deleteFileFromHome.value = true
            } else {
                requireActivity().showSnackbar(getString(R.string.errorDelete))
            }
        }
    }

    override fun openWithClicked() {
        val packageManager = requireContext().packageManager
        if (requireContext().openWithIntent(currentPreviewFile, userDrive).resolveActivity(packageManager) == null) {
            requireActivity().showSnackbar(R.string.allActivityNotFoundError)
        } else {
            safeNavigate(
                PreviewSliderFragmentDirections
                    .actionPreviewSliderFragmentToDownloadProgressDialog(
                        fileID = currentPreviewFile.id,
                        fileName = currentPreviewFile.name,
                        userDrive = userDrive
                    )
            )
        }
    }

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentPreviewFile, destinationFolder)
            .observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    mainViewModel.refreshActivities.value = true
                    requireActivity().showSnackbar(
                        getString(
                            R.string.allFileMove,
                            currentPreviewFile.name,
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
