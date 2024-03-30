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
package com.infomaniak.drive.views

import android.app.Dialog
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.ColorFolderBottomSheetDialogArgs
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialogDirections
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeNavigate

class FileInfoActionsViewController(
    override val ownerFragment: Fragment,
    override val currentFile: File,
    private val mainViewModel: MainViewModel,
    private val callbacks: Callbacks,
) : OnItemClickListener {

    private val context: Context get() = ownerFragment.requireContext()

    private fun trackFileActionEvent(name: String, value: Boolean? = null) {
        context.trackFileActionEvent(name, value = value?.toFloat())
    }


    override fun addFavoritesClicked() {
        trackFileActionEvent("favorite", !currentFile.isFavorite)

        currentFile.apply {
            val observer: Observer<ApiResponse<Boolean>> = Observer { apiResponse ->
                if (apiResponse.isSuccess()) {
                    isFavorite = !isFavorite
                    showFavoritesResultSnackbar()
                } else {
                    ownerFragment.showSnackbar(R.string.errorAddFavorite, showAboveFab = true)
                }
                callbacks.onFavoriteApiCallResult?.invoke(apiResponse.isSuccess(), this)
            }

            val userDrive = callbacks.getUserDrive?.invoke()
            val viewLifecycleOwner = callbacks.getViewLifecycleOwner()
            if (isFavorite) {
                mainViewModel.deleteFileFromFavorites(this, userDrive).observe(viewLifecycleOwner, observer)
            } else {
                mainViewModel.addFileToFavorites(this, userDrive).observe(viewLifecycleOwner, observer)
            }
        }
    }

    override fun cancelExternalImportClicked() {
        trackFileActionEvent("cancelExternalImport")

        mainViewModel.cancelExternalImport(currentFile.externalImport!!.id)
            .observe(callbacks.getViewLifecycleOwner()) { apiResponse ->
                if (!apiResponse.isSuccess()) {
                    ownerFragment.showSnackbar(context.getString(apiResponse.translatedError), showAboveFab = true)
                }
                ownerFragment.findNavController().popBackStack()
            }
    }

    override fun colorFolderClicked(color: String?) {
        context.trackEvent("colorFolder", "switch")
        ownerFragment.openColorFolderBottomSheetDialog(color)
    }

    override fun displayInfoClicked() {
        callbacks.onDisplayInfoClicked?.invoke()
    }

    override fun downloadFileClicked() {
        trackFileActionEvent("download")

        callbacks.getDrivePermissions?.invoke()?.let { drivePermissions ->
            currentFile.downloadFile(context, drivePermissions) {
                callbacks.onFileDownloaded?.invoke()
            }
        }
    }

    override fun dropBoxClicked(isDropBox: Boolean) {
        trackFileActionEvent("convertToDropbox", isDropBox)

        if (isDropBox) {
            ownerFragment.safeNavigate(
                FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToManageDropboxFragment(
                    fileId = currentFile.id,
                    fileName = currentFile.name
                )
            )
        } else {
            if (AccountUtils.getCurrentDrive()?.packFunctionality?.dropbox == true) {
                ownerFragment.safeNavigate(
                    FileInfoActionsBottomSheetDialogDirections.actionFileInfoActionsBottomSheetDialogToConvertToDropBoxFragment(
                        fileId = currentFile.id,
                        fileName = currentFile.name
                    )
                )
            } else ownerFragment.safeNavigate(R.id.dropBoxBottomSheetDialog)
        }
    }

    override fun fileRightsClicked() {
        callbacks.onFileRightsClicked?.invoke()
    }

    override fun goToFolder() {
        FileController.getParentFile(currentFile.id)?.let { folder ->
            ownerFragment.navigateToParentFolder(folder.id, mainViewModel)
        }
    }

    override fun manageCategoriesClicked(fileId: Int) {
        callbacks.onManageCategoriesClicked?.invoke(fileId)
    }

    override fun onCacheAddedToOffline() {
        mainViewModel.updateOfflineFile.value = currentFile.id
    }

    override fun onDeleteFile() {
        Utils.confirmFileDeletion(context, fileName = currentFile.name) { dialog ->
            if (ownerFragment.isResumed) {
                mainViewModel.deleteFile(currentFile, callbacks.getUserDrive?.invoke())
                    .observe(callbacks.getViewLifecycleOwner()) { apiResponse ->
                        onFileDeleted(dialog)
                        val isSuccess = apiResponse.isSuccess()
                        callbacks.onFileDeleted?.invoke(currentFile, apiResponse, isSuccess)
                    }
            } else {
                onFileDeleted(dialog)
            }
        }
    }

    override fun onDuplicateFile() {
        currentFile.apply {
            Utils.createPromptNameDialog(
                context = context,
                title = R.string.buttonDuplicate,
                fieldName = R.string.fileInfoInputDuplicateFile,
                positiveButton = R.string.buttonCopy,
                fieldValue = name,
                selectedRange = getFileName().length
            ) { dialog, name ->
                if (ownerFragment.isResumed) {
                    mainViewModel.duplicateFile(currentFile, name).observe(callbacks.getViewLifecycleOwner()) { apiResponse ->
                        callbacks.onFileDuplicated?.invoke(this, apiResponse, apiResponse.isSuccess())
                        onFileDuplicated(dialog)
                    }
                } else {
                    onFileDuplicated(dialog)
                }
            }
        }
    }

    private fun onFileDeleted(dialog: Dialog) {
        trackFileActionEvent("putInTrash")
        dialog.dismiss()
    }

    private fun onFileDuplicated(dialog: Dialog) {
        trackFileActionEvent("copy")
        dialog.dismiss()
    }

    private fun File.showFavoritesResultSnackbar() {
        ownerFragment.showSnackbar(
            title = context.getString(if (isFavorite) R.string.allFileAddFavoris else R.string.allFileDeleteFavoris, name),
            showAboveFab = true,
        )
    }

    data class Callbacks(
        val getViewLifecycleOwner: () -> LifecycleOwner,
        val getUserDrive: (() -> UserDrive)? = null,
        val getDrivePermissions: (() -> DrivePermissions)? = null,
        val onFavoriteApiCallResult: ((isSuccess: Boolean, file: File) -> Unit)? = null,
        val onDisplayInfoClicked: (() -> Unit)? = null,
        val onFileDownloaded: (() -> Unit)? = null,
        val onFileRightsClicked: (() -> Unit)? = null,
        val onManageCategoriesClicked: ((fileId: Int) -> Unit)? = null,
        val onFileDeleted: ((file: File, apiResponse: ApiResponse<CancellableAction>, succeed: Boolean) -> Unit)? = null,
        val onFileDuplicated: ((file: File, apiResponse: ApiResponse<File>, succeed: Boolean) -> Unit)? = null,
    )

    companion object {

        fun Fragment.openColorFolderBottomSheetDialog(color: String?) {
            if (AccountUtils.getCurrentDrive()?.isFreePack == true) {
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