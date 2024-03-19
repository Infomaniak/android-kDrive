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
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntent
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.set
import kotlin.math.max

class PreviewSliderFragment : Fragment(), FileInfoActionsView.OnItemClickListener {

    private var _binding: FragmentPreviewSliderBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: PreviewSliderFragmentArgs by navArgs()
    private val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var drivePermissions: DrivePermissions
    private lateinit var previewSliderAdapter: PreviewSliderAdapter
    private lateinit var userDrive: UserDrive
    private var isUiShown = false

    override val ownerFragment = this
    override lateinit var currentFile: File

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
            runCatching {
                viewPager.setCurrentItem(position, false)
            }.onFailure {
                Sentry.withScope { scope ->
                    scope.setExtra("currentFile", "id: ${currentFile.id} name: ${currentFile.name}")
                    scope.setExtra("files.values", files.values.joinToString { "id: ${it.id} name: ${it.name}" })
                    Sentry.captureException(it)
                }
                currentFile = files.values.first()
                viewPager.setCurrentItem(0, false)
            }
        }

        bottomSheetBehavior = requireActivity().getBottomSheetFileBehavior(binding.bottomSheetFileInfos, !navigationArgs.hideActions)
        setupWindowInsetsListener()
    }

    override fun onStart() {
        super.onStart()
        setupTransparentStatusBar()
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
        requireActivity().clearEdgeToEdge()
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

    private fun noPreviewList() = mainViewModel.currentPreviewFileList.isEmpty()

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_WITH) {
            context?.openWith(currentFile, userDrive)
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            _binding?.bottomSheetFileInfos?.refreshBottomSheetUi(currentFile)
        }
    }

    fun toggleFullscreen() {
        _binding?.previewSliderParent?.apply {
            val transition = Slide(Gravity.TOP).apply {
                duration = 200
                addTarget(R.id.header)
            }
            TransitionManager.beginDelayedTransition(this, transition)
            _binding?.header?.isVisible = isUiShown

            toggleBottomSheet(shouldShow = isUiShown)
            toggleSystemBar(shouldShow = isUiShown)

            isUiShown = !isUiShown
        }
    }

    private fun setupWindowInsetsListener() = with(binding) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                header.setMargins(left = left, top = top, right = right)
                val topOffset = max(top, root.height - bottomSheetFileInfos.height)
                bottomSheetBehavior.apply {
                    peekHeight = getDefaultPeekHeight() + bottom
                    expandedOffset = topOffset
                    maxHeight = root.height - topOffset
                }
                // Add padding to the bottom to allow the last element of the
                // list to be displayed right over the android navigation bar
                bottomSheetFileInfos.setPadding(0, 0, 0, bottom)
            }

            windowInsets
        }
    }

    private fun setupTransparentStatusBar() {
        activity?.window?.apply {
            statusBarColor = ContextCompat.getColor(requireContext(), R.color.previewBackgroundTransparent)

            lightStatusBar(false)
            toggleEdgeToEdge(true)
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
            val observer: Observer<ApiResponse<Boolean>> = Observer { apiResponse ->
                if (apiResponse.isSuccess()) {
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

    private fun toggleSystemBar(shouldShow: Boolean) {
        getWindowInsetsController(requireActivity().window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val systemBars = WindowInsetsCompat.Type.systemBars()
            if (shouldShow) show(systemBars) else hide(systemBars)
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

            withContext(Dispatchers.Main) {
                currentFile.isOffline = false
                binding.bottomSheetFileInfos.refreshBottomSheetUi(currentFile)
            }
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                removeFileInSlider()
                showSnackbar(R.string.snackbarLeaveShareConfirmation)
            } else {
                showSnackbar(apiResponse.translatedError)
            }
        }
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

    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) {
        mainViewModel.duplicateFile(currentFile, result).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { file ->
                    mainViewModel.currentPreviewFileList[file.id] = file
                    previewSliderAdapter.addFile(file)
                    showSnackbar(getString(R.string.allFileDuplicate, currentFile.name))
                    toggleBottomSheet(shouldShow = true)
                }
            } else {
                showSnackbar(R.string.errorDuplicate)
                toggleBottomSheet(shouldShow = true)
            }
            onApiResponse()
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
        mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                removeFileInSlider()
                showSnackbar(getString(R.string.snackbarMoveTrashConfirmation, currentFile.name))
                mainViewModel.deleteFileFromHome.value = true
            } else {
                showSnackbar(R.string.errorDelete)
            }
        }
    }

    override fun openWithClicked() {
        super.openWithClicked()
        val packageManager = requireContext().packageManager
        if (requireContext().openWithIntent(currentFile, userDrive).resolveActivity(packageManager) == null) {
            showSnackbar(R.string.errorNoSupportingAppFound)
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
                    // Because if we are on the favorite view we do not want to remove it for example
                    if (findNavController().previousBackStackEntry?.destination?.id == R.id.fileListFragment) removeFileInSlider()
                    mainViewModel.refreshActivities.value = true
                    showSnackbar(getString(R.string.allFileMove, currentFile.name, destinationFolder.name))
                } else {
                    showSnackbar(R.string.errorMove)
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

    companion object {

        fun Fragment.getPageNumberChip() = (parentFragment as? PreviewSliderFragment)?._binding?.pageNumberChip

        fun Fragment.toggleFullscreen() {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
            (activity as? PreviewPDFActivity)?.toggleFullscreen()
        }

        fun Fragment.openWithClicked() {
            (parentFragment as? PreviewSliderFragment)?.openWithClicked()
        }
    }
}
