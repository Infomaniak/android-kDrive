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

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.moveFileClicked
import com.infomaniak.drive.utils.openOnlyOfficeDocument

interface OnItemClickListener {

    val ownerFragment: Fragment
    val currentFile: File

    private val context: Context get() = ownerFragment.requireContext()

    private fun trackFileActionEvent(name: String, value: Boolean? = null) {
        context.trackFileActionEvent(name, value = value?.toFloat())
    }

    fun addFavoritesClicked()
    fun cancelExternalImportClicked()
    fun colorFolderClicked(color: String?)
    fun displayInfoClicked()
    fun downloadFileClicked()
    fun dropBoxClicked(isDropBox: Boolean)
    fun fileRightsClicked()
    fun goToFolder()
    fun manageCategoriesClicked(fileId: Int)
    fun onCacheAddedToOffline()
    fun onDeleteFile()
    fun onDuplicateFile()

    fun onLeaveShare(onApiResponse: () -> Unit) = Unit

    fun onMoveFile(destinationFolder: File) = Unit

    fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit

    @CallSuper
    fun openWithClicked() = trackFileActionEvent("openWith")

    fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit

    @CallSuper
    fun sharePublicLink(onActionFinished: () -> Unit) = trackFileActionEvent("shareLink")

    @CallSuper
    fun editDocumentClicked() {
        trackFileActionEvent("edit")
        ownerFragment.openOnlyOfficeDocument(currentFile)
    }

    fun onSelectFolderResult(data: Intent?) {
        data?.extras?.let { bundle ->
            SelectFolderActivityArgs.fromBundle(bundle).apply {
                onMoveFile(File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId))
            }
        }
    }

    fun availableOfflineSwitched(fileInfoActionsView: FileInfoActionsView, isChecked: Boolean): Boolean {
        currentFile.apply {
            when {
                isOffline && isChecked -> Unit
                !isOffline && !isChecked -> Unit
                isChecked -> {
                    trackFileActionEvent("offline", true)
                    return fileInfoActionsView.downloadAsOfflineFile()
                }
                else -> {
                    trackFileActionEvent("offline", false)
                    val offlineLocalPath = getOfflineFile(context)
                    val cacheFile = getCacheFile(context)
                    offlineLocalPath?.let { removeOfflineFile(offlineLocalPath, cacheFile) }
                }
            }
        }

        return true
    }

    fun moveFileClicked(
        folderId: Int?,
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        mainViewModel: MainViewModel
    ) {
        trackFileActionEvent("move")
        context.moveFileClicked(folderId, selectFolderResultLauncher, mainViewModel)
    }

    fun leaveShare() {
        currentFile.apply {
            Utils.createConfirmation(
                context = context,
                title = context.getString(R.string.modalLeaveShareTitle),
                message = context.getString(R.string.modalLeaveShareDescription, name),
                autoDismiss = false
            ) { dialog ->
                onLeaveShare {
                    trackFileActionEvent("stopShare")
                    dialog.dismiss()
                }
            }
        }
    }

    fun renameFileClicked() {
        currentFile.apply {
            Utils.createPromptNameDialog(
                context = context,
                title = R.string.buttonRename,
                fieldName = if (isFolder()) R.string.hintInputDirName else R.string.hintInputFileName,
                positiveButton = R.string.buttonSave,
                fieldValue = name,
                selectedRange = getFileName().length
            ) { dialog, name ->
                onRenameFile(name) {
                    trackFileActionEvent("rename")
                    dialog.dismiss()
                }
            }
        }
    }
}