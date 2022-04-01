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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.DownloadProgressDialog
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_ACTION_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_MAIN_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_TITLE_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.REFRESH_FAVORITE_FILE
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntent
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_file_info_actions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileInfoActionsBottomSheetDialog : BottomSheetDialogFragment(), FileInfoActionsView.OnItemClickListener {

    private lateinit var drivePermissions: DrivePermissions
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: FileInfoActionsBottomSheetDialogArgs by navArgs()

    override lateinit var currentFile: File
    override val ownerFragment = this

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_file_info_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentFile = FileController.getFileById(navigationArgs.fileId, navigationArgs.userDrive) ?: run {
            findNavController().popBackStack()
            return
        }

        drivePermissions = DrivePermissions().apply {
            registerPermissions(this@FileInfoActionsBottomSheetDialog) { authorized -> if (authorized) downloadFileClicked() }
        }

        fileInfoActionsView.apply {
            init(
                ownerFragment = this@FileInfoActionsBottomSheetDialog,
                mainViewModel = mainViewModel,
                onItemClickListener = this@FileInfoActionsBottomSheetDialog,
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = navigationArgs.userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)
        }

        setupBackActionHandler()
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_WITH) {
            context?.openWith(currentFile)
        }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            lifecycleScope.launchWhenResumed { fileInfoActionsView.refreshBottomSheetUi(currentFile) }
        }

        getBackNavigationResult<String>(ColorFolderBottomSheetDialog.COLOR_FOLDER_NAV_KEY) {
            updateFolderColor(it)
        }
    }

    private fun updateFolderColor(color: String) {
        if (isResumed) {
            mainViewModel.updateFolderColor(currentFile, color).observe(viewLifecycleOwner) { apiResponse ->
                findNavController().popBackStack()
                val text = if (apiResponse.isSuccess()) {
                    resources.getQuantityString(R.plurals.fileListColorFolderConfirmationSnackbar, 1)
                } else {
                    getString(apiResponse.translatedError)
                }
                requireActivity().showSnackbar(text, mainFab)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fix the popBackStack in onViewCreated because onResume is still called
        if (findNavController().currentDestination?.id != R.id.fileInfoActionsBottomSheetDialog) return
        fileInfoActionsView.updateAvailableOfflineItem()
        fileInfoActionsView.observeOfflineProgression(this) {}
    }

    override fun onPause() {
        super.onPause()
        fileInfoActionsView.removeOfflineObservations(this)
    }

    override fun editDocumentClicked() {
        findNavController().popBackStack()
        super.editDocumentClicked()
    }

    override fun displayInfoClicked() {
        currentFile.apply {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileDetailsFragment(
                    fileId = id,
                    userDrive = navigationArgs.userDrive
                )
            )
        }
    }

    override fun fileRightsClicked() {
        safeNavigate(
            FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileShareDetailsFragment(
                fileId = currentFile.id
            )
        )
    }

    override fun goToFolder() {
        FileController.getParentFile(currentFile.id)?.let { folder -> navigateToParentFolder(folder, mainViewModel) }
    }

    override fun dropBoxClicked(isDropBox: Boolean) {
        if (isDropBox) {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToManageDropboxFragment(
                    fileId = currentFile.id,
                    fileName = currentFile.name
                )
            )
        } else {
            if (AccountUtils.getCurrentDrive()?.packFunctionality?.dropbox == true) {
                safeNavigate(
                    FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToConvertToDropBoxFragment(
                        fileId = currentFile.id,
                        fileName = currentFile.name
                    )
                )
            } else safeNavigate(R.id.dropBoxBottomSheetDialog)
        }
    }

    override fun copyPublicLink() {
        super.copyPublicLink()
        fileInfoActionsView.createPublicCopyLink(onSuccess = {
            requireActivity().showSnackbar(title = R.string.fileInfoLinkCopiedToClipboard, anchorView = requireActivity().mainFab)
            findNavController().popBackStack()
        }, onError = { translatedError ->
            requireActivity().showSnackbar(translatedError, anchorView = requireActivity().mainFab)
        })
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        fileInfoActionsView.downloadFile(drivePermissions) {
            findNavController().popBackStack()
        }
    }

    override fun manageCategoriesClicked(fileId: Int) {
        safeNavigate(
            FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToSelectCategoriesFragment(
                fileId = fileId,
                categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
            )
        )
    }

    override fun colorFolderClicked(color: String) {
        super.colorFolderClicked(color)
        openColorFolderBottomSheetDialog(color)
    }

    override fun addFavoritesClicked() {
        super.addFavoritesClicked()
        currentFile.apply {
            val observer: Observer<ApiResponse<Boolean>> = Observer { apiResponse ->
                if (apiResponse.isSuccess()) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                    setBackNavigationResult(REFRESH_FAVORITE_FILE, currentFile.id)
                } else {
                    requireActivity().showSnackbar(R.string.errorAddFavorite, requireActivity().mainFab)
                    findNavController().popBackStack()
                }
            }

            if (isFavorite) {
                mainViewModel.deleteFileFromFavorites(this, navigationArgs.userDrive).observe(viewLifecycleOwner, observer)
            } else {
                mainViewModel.addFileToFavorites(this, navigationArgs.userDrive).observe(viewLifecycleOwner, observer)
            }
        }
    }

    override fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile)

            withContext(Dispatchers.Main) {
                currentFile.isOffline = false
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.offlineFileFragment) {
                    findNavController().popBackStack()
                } else {
                    fileInfoActionsView.refreshBottomSheetUi(currentFile)
                }
                mainViewModel.updateOfflineFile.value = currentFile.id
            }
        }
    }

    override fun onCacheAddedToOffline() {
        mainViewModel.updateOfflineFile.value = currentFile.id
    }

    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) {
        if (isResumed) {
            val folderId = FileController.getParentFile(currentFile.id)?.id
            mainViewModel.duplicateFile(currentFile, folderId, result).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    apiResponse.data?.let {
                        mainViewModel.refreshActivities.value = true
                        transmitActionAndPopBack(getString(R.string.allFileDuplicate, currentFile.name))
                    }
                } else {
                    transmitActionAndPopBack(getString(R.string.errorDuplicate))
                }
                onApiResponse()
            }
        } else {
            onApiResponse()
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        if (isResumed) {
            fileInfoActionsView.onRenameFile(mainViewModel, newName,
                onSuccess = { action ->
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileRename, currentFile.name),
                        action.setDriveAndReturn(currentFile.driveId)
                    )
                    onApiResponse()
                }, onError = { translatedError ->
                    transmitActionAndPopBack(translatedError)
                    onApiResponse()
                })
        } else {
            onApiResponse()
        }
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        if (isResumed) {
            mainViewModel.deleteFile(currentFile, navigationArgs.userDrive).observe(viewLifecycleOwner) { apiResponse ->
                onApiResponse()
                if (apiResponse.isSuccess()) {
                    mainViewModel.refreshActivities.value = true
                    val title = resources.getQuantityString(
                        R.plurals.snackbarMoveTrashConfirmation,
                        1,
                        currentFile.name
                    )
                    transmitActionAndPopBack(title, apiResponse.data?.setDriveAndReturn(currentFile.driveId))
                } else {
                    transmitActionAndPopBack(getString(R.string.errorDelete))
                }
            }
        } else {
            onApiResponse()
        }
    }

    override fun openWithClicked() {
        super.openWithClicked()
        if (requireContext().openWithIntent(currentFile).resolveActivity(requireContext().packageManager) == null) {
            requireActivity().showSnackbar(R.string.allActivityNotFoundError)
            findNavController().popBackStack()
        } else {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections
                    .actionFileInfoActionsBottomSheetDialogToDownloadProgressDialog(
                        fileId = currentFile.id,
                        fileName = currentFile.name,
                        userDrive = navigationArgs.userDrive
                    )
            )
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        if (isResumed) {
            mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                onApiResponse()
                if (apiResponse.isSuccess()) {
                    transmitActionAndPopBack(getString(R.string.snackbarLeaveShareConfirmation))
                    mainViewModel.refreshActivities.value = true
                } else {
                    transmitActionAndPopBack(getString(R.string.anErrorHasOccurred))
                }
            }
        } else {
            onApiResponse()
        }
    }

    override fun onMoveFile(destinationFolder: File) {
        mainViewModel.moveFile(currentFile, destinationFolder)
            .observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileMove, currentFile.name, destinationFolder.name),
                        apiResponse.data?.setDriveAndReturn(currentFile.driveId)
                    )
                } else {
                    transmitActionAndPopBack(getString(R.string.errorMove))
                }
            }
    }

    private fun File.showFavoritesResultSnackbar() {
        if (isFavorite) {
            requireActivity().showSnackbar(
                getString(R.string.allFileAddFavoris, name),
                anchorView = requireActivity().mainFab
            )
        } else {
            requireActivity().showSnackbar(
                getString(R.string.allFileDeleteFavoris, name),
                anchorView = requireActivity().mainFab
            )
        }
    }

    private fun transmitActionAndPopBack(message: String, action: CancellableAction? = null) {
        val bundle = bundleOf(CANCELLABLE_TITLE_KEY to message, CANCELLABLE_ACTION_KEY to action)
        setBackNavigationResult(CANCELLABLE_MAIN_KEY, bundle)
    }

    companion object {

        fun Fragment.openColorFolderBottomSheetDialog(color: String?) {
            if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {
                safeNavigate(R.id.colorFolderUpgradeBottomSheetDialog)
            } else {
                safeNavigate(
                    R.id.colorFolderBottomSheetDialog,
                    ColorFolderBottomSheetDialogArgs(color = color).toBundle()
                )
            }
        }
    }
}
