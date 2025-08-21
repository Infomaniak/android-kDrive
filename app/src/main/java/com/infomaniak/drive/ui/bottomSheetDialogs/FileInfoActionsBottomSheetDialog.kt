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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask.Companion.LIMIT_EXCEEDED_ERROR_CODE
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentBottomSheetFileInfoActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MainViewModel.FileResult
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_ACTION_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_MAIN_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_TITLE_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.REFRESH_FAVORITE_FILE
import com.infomaniak.drive.ui.fileList.ShareLinkViewModel
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragmentArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.navigateToParentFolder
import com.infomaniak.drive.utils.openKSuiteProBottomSheet
import com.infomaniak.drive.utils.openMyKSuiteUpgradeBottomSheet
import com.infomaniak.drive.utils.openWith
import com.infomaniak.drive.utils.shareText
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.FileInfoActionsView
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener.Companion.downloadFile
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.lib.core.utils.whenResultIsOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileInfoActionsBottomSheetDialog : BottomSheetDialogFragment(), FileInfoActionsView.OnItemClickListener {

    private var binding: FragmentBottomSheetFileInfoActionsBinding by safeBinding()

    private lateinit var downloadPermissions: DrivePermissions
    private val mainViewModel: MainViewModel by activityViewModels()
    private val shareLinkViewModel: ShareLinkViewModel by viewModels()
    private val navigationArgs: FileInfoActionsBottomSheetDialogArgs by navArgs()

    override val ownerFragment = this
    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data -> onSelectFolderResult(data) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetFileInfoActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentFile = FileController.getFileById(navigationArgs.fileId, navigationArgs.userDrive) ?: run {
            findNavController().popBackStack()
            return
        }

        downloadPermissions = DrivePermissions(DrivePermissions.Type.DownloadingWithDownloadManager).apply {
            registerPermissions(this@FileInfoActionsBottomSheetDialog) { authorized -> if (authorized) downloadFileClicked() }
        }

        binding.fileInfoActionsView.apply {
            init(
                ownerFragment = this@FileInfoActionsBottomSheetDialog,
                mainViewModel = mainViewModel,
                shareLinkViewModel = shareLinkViewModel,
                onItemClickListener = this@FileInfoActionsBottomSheetDialog,
                selectFolderResultLauncher = selectFolderResultLauncher,
                isSharedWithMe = navigationArgs.userDrive.sharedWithMe,
            )
            updateCurrentFile(currentFile)
            setPrintVisibility(isGone = true)
        }

        setupBackActionHandler()
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Int>(DownloadAction.OPEN_WITH.value) { context?.openWith(currentFile, navigationArgs.userDrive) }

        getBackNavigationResult<Any>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            lifecycleScope.launchWhenResumed { binding.fileInfoActionsView.refreshBottomSheetUi(currentFile) }
        }

        getBackNavigationResult(ColorFolderBottomSheetDialog.COLOR_FOLDER_NAV_KEY, ::updateFolderColor)
    }

    private fun updateFolderColor(color: String) {
        if (isResumed) {
            mainViewModel.updateFolderColor(currentFile, color, navigationArgs.userDrive)
                .observe(viewLifecycleOwner) { fileRequest ->
                    findNavController().popBackStack()
                    val snackbarText = if (fileRequest.isSuccess) {
                        resources.getQuantityString(R.plurals.fileListColorFolderConfirmationSnackbar, 1)
                    } else {
                        fileRequest.errorResId?.let { getString(it) }
                    }
                    snackbarText?.let { text -> showSnackbar(text, showAboveFab = true) }
                }
        }
    }

    override fun onResume() = with(binding) {
        super.onResume()
        // Fix the popBackStack in onViewCreated because onResume is still called
        if (findNavController().currentDestination?.id != R.id.fileInfoActionsBottomSheetDialog) return
        fileInfoActionsView.observeOfflineProgression(this@FileInfoActionsBottomSheetDialog)
        fileInfoActionsView.updateAvailableOfflineItem()
    }

    override fun onPause() {
        super.onPause()
        binding.fileInfoActionsView.removeOfflineObservations(this)
    }

    override fun editDocumentClicked(mainViewModel: MainViewModel) {
        findNavController().popBackStack()
        super.editDocumentClicked(mainViewModel)
    }

    override fun displayInfoClicked() {
        currentFile.apply {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileDetailsFragment(
                    fileId = id,
                    userDrive = navigationArgs.userDrive,
                )
            )
        }
    }

    override fun fileRightsClicked() {
        safeNavigate(
            FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToFileShareDetailsFragment(
                fileId = currentFile.id,
            )
        )
    }

    override fun goToFolder() {
        FileController.getParentFile(currentFile.id)?.let { folder -> navigateToParentFolder(folder.id, mainViewModel) }
    }

    override fun dropBoxClicked(isDropBox: Boolean, canCreateDropbox: Boolean, kSuite: KSuite?, isAdmin: Boolean) {
        super.dropBoxClicked(isDropBox, canCreateDropbox, kSuite, isAdmin)
        if (isDropBox) {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToManageDropboxFragment(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                )
            )
        } else {
            if (canCreateDropbox) {
                safeNavigate(
                    FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToConvertToDropBoxFragment(
                        fileId = currentFile.id,
                        fileName = currentFile.name,
                    )
                )
            } else {
                val matomoName = "convertToDropbox"
                if (kSuite?.isProUpgradable() == true) {
                    openKSuiteProBottomSheet(kSuite, isAdmin, matomoName)
                } else {
                    openMyKSuiteUpgradeBottomSheet(matomoName)
                }
            }
        }
    }

    override fun sharePublicLink(onActionFinished: () -> Unit) {
        super.sharePublicLink(onActionFinished)
        binding.fileInfoActionsView.createPublicShareLink(
            onSuccess = {
                context?.shareText(text = it)
                findNavController().popBackStack()
                onActionFinished()
            },
            onError = { translatedError ->
                showSnackbar(translatedError, showAboveFab = true)
                onActionFinished()
            },
        )
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        currentContext.downloadFile(downloadPermissions, currentFile) { findNavController().popBackStack() }
    }

    override fun manageCategoriesClicked(fileId: Int) {
        openManageCategoriesBottomSheetDialog(intArrayOf(fileId), navigationArgs.userDrive)
    }

    override fun colorFolderClicked(color: String?) {
        super.colorFolderClicked(color)
        openColorFolderBottomSheetDialog(color)
    }

    override fun addFavoritesClicked() {
        super.addFavoritesClicked()
        currentFile.apply {
            val observer: Observer<FileResult> = Observer { fileRequest ->
                if (fileRequest.isSuccess) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                    setBackNavigationResult(REFRESH_FAVORITE_FILE, currentFile.id)
                } else {
                    showSnackbar(R.string.errorAddFavorite, showAboveFab = true)
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

    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) {
        lifecycleScope.launch {
            mainViewModel.removeOfflineFile(currentFile, offlineLocalPath, cacheFile)
            withContext(Dispatchers.Main) {
                currentFile.isOffline = false
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.offlineFileFragment) {
                    findNavController().popBackStack()
                } else {
                    binding.fileInfoActionsView.refreshBottomSheetUi(currentFile)
                }
                mainViewModel.updateOfflineFile.value = currentFile.id
            }
        }
    }

    override fun onCacheAddedToOffline() {
        mainViewModel.updateOfflineFile.value = currentFile.id
    }

    override fun onDuplicateFile(destinationFolder: File) {
        mainViewModel.duplicateFile(currentFile, destinationFolder.id).observe(viewLifecycleOwner) { apiResponse ->
            val snackbarMessage = if (apiResponse.isSuccess) {
                mainViewModel.refreshActivities.value = true
                getString(R.string.allFileDuplicate, currentFile.name)
            } else if (apiResponse.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                getString(R.string.errorFilesLimitExceeded)
            } else {
                getString(R.string.errorDuplicate)
            }

            transmitActionAndPopBack(snackbarMessage)
        }
    }

    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) {
        if (isResumed) {
            binding.fileInfoActionsView.onRenameFile(
                newName = newName,
                onSuccess = { action ->
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileRename, currentFile.name),
                        action.setDriveAndReturn(currentFile.driveId)
                    )
                    onApiResponse()
                },
                onError = { translatedError ->
                    transmitActionAndPopBack(translatedError)
                    onApiResponse()
                }
            )
        } else {
            onApiResponse()
        }
    }

    override fun onDeleteFile(onApiResponse: () -> Unit) {
        if (isResumed) {
            mainViewModel.deleteFile(currentFile, navigationArgs.userDrive).observe(viewLifecycleOwner) { fileRequest ->
                onApiResponse()
                if (fileRequest.isSuccess) {
                    mainViewModel.refreshActivities.value = true
                    val title = getString(R.string.snackbarMoveTrashConfirmation, currentFile.name)
                    val cancellableAction = fileRequest.data as? CancellableAction
                    transmitActionAndPopBack(title, cancellableAction?.setDriveAndReturn(currentFile.driveId))
                } else {
                    transmitActionAndPopBack(getString(R.string.errorDelete))
                }
            }
        } else {
            onApiResponse()
        }
    }

    override fun openWith() {
        context?.openWith(ownerFragment = ownerFragment, currentFile = currentFile, userDrive = navigationArgs.userDrive) {
            safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToDownloadProgressDialog(
                    fileId = currentFile.id,
                    fileName = currentFile.name,
                    userDrive = navigationArgs.userDrive,
                )
            )
        }
    }

    override fun onLeaveShare(onApiResponse: () -> Unit) {
        if (isResumed) {
            mainViewModel.deleteFile(currentFile).observe(viewLifecycleOwner) { fileRequest ->
                onApiResponse()
                if (fileRequest.isSuccess) {
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

    override fun onMoveFile(destinationFolder: File, isSharedWithMe: Boolean) {
        mainViewModel.moveFile(currentFile, destinationFolder, isSharedWithMe)
            .observe(viewLifecycleOwner) { fileRequest ->
                if (fileRequest.isSuccess) {
                    mainViewModel.refreshActivities.value = true
                    transmitActionAndPopBack(
                        getString(R.string.allFileMove, currentFile.name, destinationFolder.name),
                        (fileRequest.data as? CancellableAction)?.setDriveAndReturn(currentFile.driveId)
                    )
                } else {
                    val resource = if (fileRequest.errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
                        R.string.errorFilesLimitExceeded
                    } else {
                        R.string.errorMove
                    }

                    transmitActionAndPopBack(getString(resource))
                }
            }
    }

    override fun cancelExternalImportClicked() {
        super.cancelExternalImportClicked()

        mainViewModel.cancelExternalImport(currentFile.externalImport!!.id).observe(viewLifecycleOwner) { apiResponse ->
            if (!apiResponse.isSuccess()) {
                showSnackbar(requireContext().getString(apiResponse.translateError()), showAboveFab = true)
            }
            findNavController().popBackStack()
        }
    }

    private fun File.showFavoritesResultSnackbar() {
        showSnackbar(
            title = getString(if (isFavorite) R.string.allFileAddFavoris else R.string.allFileDeleteFavoris, name),
            showAboveFab = true,
        )
    }

    private fun transmitActionAndPopBack(message: String, action: CancellableAction? = null) {
        val bundle = bundleOf(CANCELLABLE_TITLE_KEY to message, CANCELLABLE_ACTION_KEY to action)
        setBackNavigationResult(CANCELLABLE_MAIN_KEY, bundle)
    }

    override fun shareFile() = Unit
    override fun saveToKDrive() = Unit
    override fun printClicked() = Unit

    companion object {

        fun Fragment.openManageCategoriesBottomSheetDialog(
            filesIds: IntArray,
            userDrive: UserDrive? = null,
            currentClassName: String? = null,
        ) {
            val args = SelectCategoriesFragmentArgs(CategoriesUsageMode.MANAGED_CATEGORIES, filesIds, userDrive = userDrive)
            safeNavigate(R.id.selectCategoriesFragment, args.toBundle(), currentClassName = currentClassName)
        }

        fun Fragment.openColorFolderBottomSheetDialog(color: String?) {
            val drive = AccountUtils.getCurrentDrive() ?: return
            val matomoName = "colorFolder"
            when {
                drive.isKSuitePersoFree -> openMyKSuiteUpgradeBottomSheet(matomoName)
                drive.isKSuiteProUpgradable -> openKSuiteProBottomSheet(drive.kSuite!!, drive.isAdmin, matomoName)
                else -> {
                    safeNavigate(R.id.colorFolderBottomSheetDialog, ColorFolderBottomSheetDialogArgs(color = color).toBundle())
                }
            }
        }
    }
}
