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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
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
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntent
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiController.gson
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
        return inflater.inflate(R.layout.fragment_preview_slider, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (previewSliderViewModel.currentPreview == null) {
            currentPreviewFile = arguments?.getInt(PREVIEW_FILE_TAG)?.let { fileId ->
                FileController.getFileById(fileId) ?: mainViewModel.currentFileList.value?.first { it.id == fileId }
            } ?: throw Exception("No current preview found")
            previewSliderViewModel.isSharedWithMe = arguments?.getBoolean(PREVIEW_IS_SHARED_WITH_ME, false) ?: false
            previewSliderViewModel.currentPreview = currentPreviewFile
            hideActions = arguments?.getBoolean(PREVIEW_HIDE_ACTIONS, false) ?: false
        } else {
            previewSliderViewModel.currentPreview?.let { currentPreviewFile = it }
        }

        userDrive = UserDrive(driveId = currentPreviewFile.driveId, sharedWithMe = previewSliderViewModel.isSharedWithMe)

        getBackNavigationResult<Boolean>(DownloadProgressDialog.OPEN_WITH) {
            requireContext().openWith(currentPreviewFile, userDrive)
        }

        drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { autorized -> if (autorized) downloadFileClicked() }

        previewSliderAdapter = PreviewSliderAdapter(childFragmentManager, lifecycle)
        viewPager.adapter = previewSliderAdapter
        viewPager.offscreenPageLimit = 1

        bottomSheetFileInfos.init(this, this, previewSliderViewModel.isSharedWithMe)
        bottomSheetFileInfos.updateCurrentFile(currentPreviewFile)
        bottomSheetFileInfos.setOnTouchListener { _, _ -> true }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPreviewFile = previewSliderAdapter.getFile(position)
                editButton.visibility = if (currentPreviewFile.isOnlyOfficePreview()) VISIBLE else GONE
                openWithButton.visibility = if (!currentPreviewFile.isOnlyOfficePreview()) VISIBLE else GONE
                bottomSheetFileInfos.openWith.visibility = VISIBLE

                lifecycleScope.launchWhenResumed {
                    withContext(Dispatchers.Main) {
                        bottomSheetFileInfos.updateCurrentFile(currentPreviewFile)
                    }
                }
            }
        })

        previewSliderViewModel.pdfIsDownloading.observe(viewLifecycleOwner) { isDownloading ->
            val visibility = if (isDownloading) GONE else VISIBLE
            if (!currentPreviewFile.isOnlyOfficePreview() && openWithButton.isVisible != !isDownloading) {
                openWithButton.visibility = visibility
            }
            bottomSheetFileInfos.openWith.visibility = visibility
        }

        editButton.setOnClickListener {
            requireContext().openOnlyOfficeDocument(findNavController(), currentPreviewFile)
        }

        openWithButton.setOnClickListener { openWithClicked() }
        backButton.setOnClickListener { findNavController().popBackStack() }

        mainViewModel.currentFileList.observe(viewLifecycleOwner) {
            it?.let { files ->
                previewSliderAdapter.setFiles(files)
                val position = previewSliderAdapter.getPosition(currentPreviewFile)
                viewPager.setCurrentItem(position, false)
            }
        }

        configureBottomSheetFileInfo()
    }

    private var showUi = false
    fun toggleFullscreen() {
        val transition = Slide(Gravity.TOP)
        transition.duration = 200
        transition.addTarget(R.id.header)
        TransitionManager.beginDelayedTransition(previewSliderParent, transition)
        header.visibility = if (showUi) VISIBLE else GONE
        toggleBottomSheet(showUi)
        showUi = !showUi
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

        bottomSheetFileInfos.observeOfflineProgression(this) { fileId ->
            previewSliderAdapter.updateFile(fileId) { file -> file.isOffline = true }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onSelectFolderResult(requestCode, resultCode, data)
    }

    fun toggleBottomSheet(show: Boolean? = null) {
        val mustShow = show ?: (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN)
        bottomSheetFileInfos?.scrollToTop()
        bottomSheetBehavior.state = if (mustShow) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.statusBarColor = ContextCompat.getColor(requireContext(), R.color.previewBackground)
        activity?.window?.lightStatusBar(false)
    }

    override fun onPause() {
        super.onPause()
        previewSliderViewModel.currentPreview = currentPreviewFile
    }

    override fun onDestroy() {
        if (this.isVisible) {
            arguments?.putString(PREVIEW_FILE_TAG, gson.toJson(currentPreviewFile, File::class.java))
        }
        super.onDestroy()
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
        currentPreviewFile.apply {
            safeNavigate(
                PreviewSliderFragmentDirections.actionPreviewSliderFragmentToFileShareDetailsFragment(
                    fileId = id,
                    fileName = name,
                    fileType = type
                )
            )
        }
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

    private fun File.showFavoritesResultSnackbar() {
        if (isFavorite) {
            requireActivity().showSnackbar(getString(R.string.allFileAddFavoris, name))
        } else {
            requireActivity().showSnackbar(getString(R.string.allFileDeleteFavoris, name))
        }
    }

    override fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentPreviewFile.id, offlineLocalPath, cacheFile)
            previewSliderAdapter.updateFile(currentPreviewFile.id) { file -> file.isOffline = false }

            withContext(Dispatchers.Main) {
                currentPreviewFile.isOffline = false
                bottomSheetFileInfos.refreshBottomSheetUi(currentPreviewFile)
            }
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(requireContext(), currentPreviewFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                if (previewSliderAdapter.deleteFile(currentPreviewFile)) {
                    findNavController().popBackStack()
                } else {
                    toggleBottomSheet(true)
                }
                mainViewModel.currentFileList.value?.remove(currentPreviewFile)
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

    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) {
        val folderID = mainViewModel.currentFolder.value?.id
        mainViewModel.duplicateFile(currentPreviewFile, folderID, result).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { file ->
                    mainViewModel.currentFileList.value?.add(file)
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
        mainViewModel.deleteFile(requireContext(), currentPreviewFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                mainViewModel.currentFileList.value?.remove(currentPreviewFile)
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

    class PreviewSliderViewModel : ViewModel() {
        val pdfIsDownloading = MutableLiveData<Boolean>()
        var currentPreview: File? = null
        var isSharedWithMe = false
    }

    companion object {
        const val PREVIEW_FILE_TAG = "previewFile"
        const val PREVIEW_IS_SHARED_WITH_ME = "isSharedWithMe"
        const val PREVIEW_HIDE_ACTIONS = "hideActions"
    }
}