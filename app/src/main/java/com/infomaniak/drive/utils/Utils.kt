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
package com.infomaniak.drive.utils

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.liveData
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getCloudAndFileUris
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.BaseDownloadWorker
import com.infomaniak.drive.data.services.BulkDownloadWorker
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.databinding.DialogDownloadProgressBinding
import com.infomaniak.drive.databinding.DialogNamePromptBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MainViewModel.FileResult
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectFragment
import com.infomaniak.drive.ui.fileList.preview.PreviewPDFActivity
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragmentArgs
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.drive.views.FileInfoActionsView.Companion.SINGLE_OPERATION_CUSTOM_TAG
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.lib.core.utils.showToast
import java.util.Date
import kotlin.math.pow

object Utils {

    const val ROOT_ID = 1
    const val OTHER_ROOT_ID = -1

    const val INDETERMINATE_PROGRESS = -1

    fun createConfirmation(
        context: Context,
        title: String,
        message: String? = null,
        buttonText: String? = null,
        autoDismiss: Boolean = true,
        isDeletion: Boolean = false,
        onConfirmation: (dialog: Dialog) -> Unit
    ): AlertDialog {
        val style = if (isDeletion) R.style.DeleteDialogStyle else R.style.DialogStyle
        val dialog = MaterialAlertDialogBuilder(context, style)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText ?: context.getString(R.string.buttonConfirm)) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onConfirmation(dialog)
            if (autoDismiss) {
                dialog.dismiss()
            } else {
                it.isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            }
        }
        return dialog
    }

    fun confirmFileDeletion(
        context: Context,
        fileName: String? = null,
        fileCount: Int = 1,
        fromTrash: Boolean = false,
        onConfirmation: (dialog: Dialog) -> Unit
    ) {
        val title: Int = if (fromTrash) R.string.modalDeleteTitle else R.string.modalMoveTrashTitle
        val message: String = context.resources.getQuantityString(
            if (fromTrash) R.plurals.modalDeleteDescription
            else R.plurals.modalMoveTrashDescription,
            fileCount,
            fileName ?: fileCount
        )
        val button: Int = if (fromTrash) R.string.buttonDelete else R.string.buttonMove

        createConfirmation(
            context = context,
            title = context.getString(title),
            message = message,
            autoDismiss = false,
            isDeletion = true,
            buttonText = context.getString(button),
            onConfirmation = onConfirmation
        )
    }

    fun createPromptNameDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes fieldName: Int,
        @StringRes positiveButton: Int,
        @DrawableRes iconRes: Int? = null,
        fieldValue: String? = null,
        selectedRange: Int? = null,
        onPositiveButtonClicked: (dialog: Dialog, body: String) -> Unit
    ) {
        val promptLayoutBinding = DialogNamePromptBinding.inflate(LayoutInflater.from(context))
        val nameEditText = promptLayoutBinding.nameEditText
        promptLayoutBinding.nameLayout.setHint(fieldName)

        iconRes?.let {
            promptLayoutBinding.icon.apply {
                setImageDrawable(ContextCompat.getDrawable(context, it))
                isVisible = true
            }
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.DialogStyle)
            .setTitle(title)
            .setView(promptLayoutBinding.root)
            .setPositiveButton(positiveButton) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .create()
            .apply {
                show()
                showKeyboard()
            }

        val buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        nameEditText.apply {
            doOnTextChanged { text, _, _, _ -> buttonPositive.isEnabled = text?.length != 0 }
            fieldValue?.let {
                setText(it)
                selectedRange?.let { range -> setSelection(0, range) }
                requestFocus()
            }
        }

        buttonPositive.setOnClickListener {
            onPositiveButtonClicked(dialog, nameEditText.text.toString().trim())
            it.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
    }

    /**
     * Specific method which allows to popBackstack by ignoring create folder "stack" (in case of folder creation)
     */
    fun ignoreCreateFolderBackStack(navController: NavController, mustIgnore: Boolean) {
        when {
            mustIgnore -> if (!navController.popBackStack(R.id.newFolderFragment, true)) navController.popBackStack()
            else -> navController.popBackStack()
        }
    }

    fun displayFile(
        mainViewModel: MainViewModel,
        navController: NavController,
        selectedFile: File,
        fileList: List<File>,
        isSharedWithMe: Boolean = false,
    ) {
        mainViewModel.currentPreviewFileList = fileList.associateBy { it.id } as LinkedHashMap<Int, File>

        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.fragment_open_enter)
            .setExitAnim(R.anim.fragment_open_exit)
            .build()

        val (destinationClass, bundle) = if (selectedFile.isPublicShared()) {
            R.id.publicSharePreviewSliderFragment to null
        } else {
            val args = PreviewSliderFragmentArgs(
                fileId = selectedFile.id,
                driveId = selectedFile.driveId,
                isSharedWithMe = isSharedWithMe,
                hideActions = selectedFile.isFromActivities,
            )

            R.id.previewSliderFragment to args.toBundle()
        }

        navController.navigate(destinationClass, bundle, navOptions)
    }

    fun convertBytesToGigaBytes(bytes: Long) = (bytes / 1024.0.pow(3))
    fun convertGigaByteToBytes(gigaBytes: Double) = (gigaBytes * 1024.0.pow(3)).toLong()

    fun Context.moveFileClicked(
        disabledFolderId: Int?,
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        mainViewModel: MainViewModel,
    ) {
        mainViewModel.ignoreSyncOffline = true
        Intent(this, SelectFolderActivity::class.java).apply {
            putExtras(
                SelectFolderActivityArgs(
                    userId = AccountUtils.currentUserId,
                    driveId = AccountUtils.currentDriveId,
                    folderId = disabledFolderId ?: -1,
                    disabledFolderId = disabledFolderId ?: -1,
                    customArgs = bundleOf(
                        MultiSelectFragment.BULK_OPERATION_CUSTOM_TAG to BulkOperationType.MOVE,
                        SINGLE_OPERATION_CUSTOM_TAG to SingleOperation.MOVE.name,
                    ),
                ).toBundle(),
            )
        }.also(selectFolderResultLauncher::launch)
    }

    fun Context.duplicateFilesClicked(
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        mainViewModel: MainViewModel,
    ) {
        Intent(this, SelectFolderActivity::class.java).apply {
            putExtras(
                SelectFolderActivityArgs(
                    userId = AccountUtils.currentUserId,
                    driveId = AccountUtils.currentDriveId,
                    folderId = mainViewModel.currentFolder.value?.id ?: -1,
                    customArgs = bundleOf(
                        MultiSelectFragment.BULK_OPERATION_CUSTOM_TAG to BulkOperationType.COPY,
                        SINGLE_OPERATION_CUSTOM_TAG to SingleOperation.COPY.name,
                    ),
                ).toBundle(),
            )
        }.also(selectFolderResultLauncher::launch)
    }

    fun Context.openWith(uri: Uri, type: String?, flags: Int) {
        startActivityFor(openWithIntentExceptkDrive(uri, type, flags))
    }

    fun Context.openWith(file: File, userDrive: UserDrive) {
        startActivityFor(openWithIntentExceptkDrive(file, userDrive))
    }

    private fun Context.startActivityFor(openWithIntent: Intent) {
        try {
            startActivity(openWithIntent)
        } catch (e: ActivityNotFoundException) {
            showToast(R.string.errorNoSupportingAppFound)
        }
    }

    fun Context.openWithIntentExceptkDrive(file: File, userDrive: UserDrive): Intent {
        val (cloudUri, uri) = file.getCloudAndFileUris(this, userDrive)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        return openWithIntentExceptkDrive(uri, contentResolver.getType(cloudUri), flags)
    }

    private fun Context.intentExcludingPdfReader(openWithIntent: Intent): Intent {
        val components = arrayOf(ComponentName(this, PreviewPDFActivity::class.java))
        return Intent.createChooser(openWithIntent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components)
    }

    fun Context.openWithIntentExceptkDrive(uri: Uri, type: String?, flags: Int): Intent {
        val openWithIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            this.flags = flags
            setDataAndType(uri, type)
        }

        // We only do that when we try to openWith with a PDF because we have our own PDF reader
        // Title in the chooser might not be displayed, at the discretion of the brand manufacturer
        // So we keep the ACTION_VIEW for every type of files EXCEPT PDF files
        return if (type == "application/pdf") applicationContext.intentExcludingPdfReader(openWithIntent) else openWithIntent
    }

    fun moveCacheFileToOffline(file: File, cacheFile: IOFile, offlineFile: IOFile) {
        if (offlineFile.exists()) offlineFile.delete()
        cacheFile.copyTo(offlineFile)
        cacheFile.delete()
        offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
    }

    fun downloadAsOfflineFile(context: Context, file: File, userDrive: UserDrive = UserDrive()) {
        val workManager = WorkManager.getInstance(context)

        if (file.isMarkedAsOffline) workManager.cancelAllWorkByTag(file.getWorkerTag())
        val inputData = workDataOf(
            BaseDownloadWorker.FILE_ID to file.id,
            DownloadWorker.FILE_NAME to file.name,
            BaseDownloadWorker.USER_ID to userDrive.userId,
            BaseDownloadWorker.DRIVE_ID to userDrive.driveId,
        )
        val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(file.getWorkerTag())
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpeditedIfAvailable()
            .build()

        workManager.enqueueUniqueWork(DownloadWorker.TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, downloadRequest)
    }

    fun downloadAsOfflineFiles(context: Context, folderId: Int, userDrive: UserDrive = UserDrive(), onSuccess: () -> Unit) =
        liveData {
            enqueueBulkDownloadWorker(context, folderId, userDrive)

            onSuccess.invoke()
            emit(FileResult(isSuccess = true))
        }

    fun enqueueBulkDownloadWorker(context: Context, folderId: Int, userDrive: UserDrive = UserDrive()) {
        val workManager = WorkManager.getInstance(context)
        val inputData = workDataOf(
            BulkDownloadWorker.FOLDER_ID to folderId,
            BaseDownloadWorker.USER_ID to userDrive.userId,
            BaseDownloadWorker.DRIVE_ID to userDrive.driveId,
        )
        val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()
        val downloadRequest = OneTimeWorkRequestBuilder<BulkDownloadWorker>()
            .addTag(BulkDownloadWorker::class.java.toString())
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpeditedIfAvailable()
            .build()

        workManager.enqueueUniqueWork(BulkDownloadWorker.TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, downloadRequest)
    }

    fun getInvalidFileNameCharacter(fileName: String): String? = DownloadManagerUtils.regexInvalidSystemChar.find(fileName)?.value

    fun copyDataToUploadCache(context: Context, file: IOFile, fileModifiedAt: Date): Uri {
        val outputFile = IOFile(context.uploadFolder, file.toUri().hashCode().toString()).apply {
            if (exists()) delete()
            setLastModified(fileModifiedAt.time)
        }
        file.copyTo(outputFile, true)
        return outputFile.toUri()
    }

    fun createProgressDialog(context: Context, title: Int): AlertDialog {
        return MaterialAlertDialogBuilder(context, R.style.DialogStyle).apply {
            setTitle(title)
            setCancelable(false)
            DialogDownloadProgressBinding.inflate(LayoutInflater.from(context)).apply {
                icon.isGone = true
                downloadProgressIndicator.isIndeterminate = true
                setView(root)
            }
        }.create()
    }

    enum class Shortcuts(val id: String) {
        UPLOAD("upload"),
        SCAN("scan"),
        SEARCH("search"),
        FEEDBACK("feedback"),
    }
}
