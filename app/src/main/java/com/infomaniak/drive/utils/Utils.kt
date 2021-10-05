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
package com.infomaniak.drive.utils

import android.app.Dialog
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CountDownTimer
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.work.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.drive.R
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment
import kotlinx.android.synthetic.main.dialog_download_progress.view.*
import kotlinx.android.synthetic.main.dialog_name_prompt.view.*
import kotlinx.android.synthetic.main.dialog_name_prompt.view.icon
import java.util.*
import kotlin.math.min
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
    ) {
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
            if (fileName == null) fileCount else 1,
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

    fun getRootName(context: Context): String {
        return context.getString(R.string.allRootName, AccountUtils.getCurrentDrive()?.name)
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
        val promptLayoutView = View.inflate(context, R.layout.dialog_name_prompt, null)
        val nameEditText = promptLayoutView.nameEditText
        promptLayoutView.nameLayout.setHint(fieldName)

        iconRes?.let {
            promptLayoutView.icon.let { iconView ->
                iconView.setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                iconView.visibility = VISIBLE
            }
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.DialogStyle)
            .setTitle(title)
            .setView(promptLayoutView)
            .setPositiveButton(positiveButton) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .create()
        dialog.show()

        val buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        nameEditText.doOnTextChanged { text, _, _, _ ->
            buttonPositive.isEnabled = text?.length != 0
        }
        fieldValue?.let {
            nameEditText.setText(it)
            selectedRange?.let { range ->
                nameEditText.setSelection(0, range)
            }
            nameEditText.requestFocus()
        }

        buttonPositive.setOnClickListener {
            onPositiveButtonClicked(dialog, nameEditText.text.toString())
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
        fileList: ArrayList<File>,
        isSharedWithMe: Boolean = false
    ) {
        mainViewModel.currentFileList.value = fileList
        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.fragment_open_enter)
            .setExitAnim(R.anim.fragment_open_exit)
            .build()
        val bundle = bundleOf(
            PreviewSliderFragment.PREVIEW_FILE_ID_TAG to selectedFile.id,
            PreviewSliderFragment.PREVIEW_FILE_DRIVE_ID to selectedFile.driveId,
            PreviewSliderFragment.PREVIEW_IS_SHARED_WITH_ME to isSharedWithMe,
            PreviewSliderFragment.PREVIEW_HIDE_ACTIONS to selectedFile.isFromActivities
        )
        navController.navigate(R.id.previewSliderFragment, bundle, navOptions)
    }

    fun convertBytesToGigaBytes(gigaByte: Long) = (gigaByte / 1024.0.pow(3)).toLong()
    fun convertGigaByteToBytes(bytes: Long) = (bytes * 1024.0.pow(3)).toLong()

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboard?.apply { setPrimaryClip(ClipData.newPlainText(text, text)) }
    }

    fun moveFileClicked(ownerFragment: Fragment, currentFolder: Int) {
        val intent = Intent(ownerFragment.context, SelectFolderActivity::class.java).apply {
            putExtra(SelectFolderActivity.USER_ID_TAG, AccountUtils.currentUserId)
            putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, AccountUtils.currentDriveId)
            putExtra(SelectFolderActivity.DISABLE_SELECTED_FOLDER_TAG, currentFolder)
            putExtra(
                SelectFolderActivity.CUSTOM_ARGS_TAG,
                bundleOf(SelectFolderActivity.BULK_OPERATION_CUSTOM_TAG to BulkOperationType.MOVE)
            )
        }
        ownerFragment.startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
    }

    fun Context.openWith(file: File, userDrive: UserDrive = UserDrive()) {
        try {
            startActivity(openWithIntent(file, userDrive))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.allActivityNotFoundError, Toast.LENGTH_LONG).show()
        }
    }

    fun Context.openWithIntent(file: File, userDrive: UserDrive = UserDrive()): Intent {
        val cloudUri = CloudStorageProvider.createShareFileUri(this, file, userDrive)!!
        val offlineFile = file.getOfflineFile(this, userDrive.userId)
        val uri = if (file.isOffline && offlineFile != null) {
            FileProvider.getUriForFile(this, getString(R.string.FILE_AUTHORITY), offlineFile)
        } else cloudUri
        return Intent().apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            setDataAndType(uri, contentResolver.getType(cloudUri))
        }
    }

    fun moveCacheFileToOffline(file: File, cacheFile: java.io.File, offlineFile: java.io.File) {
        if (offlineFile.exists()) offlineFile.delete()
        cacheFile.copyTo(offlineFile)
        cacheFile.delete()
        offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
    }

    fun downloadAsOfflineFile(context: Context, file: File, userDrive: UserDrive = UserDrive()) {
        if (file.isPendingOffline(context)) return
        val inputData = workDataOf(
            DownloadWorker.FILE_ID to file.id,
            DownloadWorker.USER_ID to userDrive.userId,
            DownloadWorker.DRIVE_ID to userDrive.driveId,
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
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(DownloadWorker.TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, downloadRequest)
    }

    fun showSnackbar(
        view: View,
        title: String,
        anchorView: View? = null,
        actionButtonTitle: Int = R.string.buttonCancel,
        onActionClicked: (() -> Unit)? = null
    ) {
        Snackbar.make(view, title, Snackbar.LENGTH_LONG).apply {
            anchorView?.let { this.anchorView = it }
            onActionClicked?.let { action ->
                setAction(actionButtonTitle) {
                    action()
                }
            }
            show()
        }
    }

    fun showSnackbar(
        view: View,
        title: Int,
        anchorView: View? = null,
        actionButtonTitle: Int = R.string.buttonCancel,
        onActionClicked: (() -> Unit)? = null
    ) {
        showSnackbar(view, view.context.getString(title), anchorView, actionButtonTitle, onActionClicked)
    }

    fun copyDataToUploadCache(context: Context, uri: Uri, fileModifiedAt: Date): Uri {
        val folder = java.io.File(context.cacheDir, UploadWorker.UPLOAD_FOLDER).apply { if (!exists()) mkdirs() }
        val outputFile = java.io.File(folder, uri.hashCode().toString()).also { if (it.exists()) it.delete() }
        if (outputFile.createNewFile()) {
            outputFile.setLastModified(fileModifiedAt.time)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outputFile.toUri()
    }

    /**
     * From file path
     */
    @Deprecated(message = "Only for API 28 and below, otherwise use ThumbnailUtils.createImageThumbnail()")
    fun extractThumbnail(filePath: String, width: Int, height: Int): Bitmap? {
        val bitmapOptions = BitmapFactory.Options()
        bitmapOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, bitmapOptions)

        val widthScale = bitmapOptions.outWidth.toFloat() / width
        val heightScale = bitmapOptions.outHeight.toFloat() / height
        val scale = min(widthScale, heightScale)
        var sampleSize = 1
        while (sampleSize < scale) {
            sampleSize *= 2
        }
        bitmapOptions.inSampleSize = sampleSize
        bitmapOptions.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(filePath, bitmapOptions)
    }

    fun getRealPathFromExternalStorage(context: Context, uri: Uri): String {
        var filePath = ""
        // ExternalStorageProvider
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":").dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = split.first()
        val relativePath = split.getOrNull(1) ?: return ""

        return if ("primary".equals(type, true)) {
            Environment.getExternalStorageDirectory().toString() + "/" + relativePath
        } else {
            val external = context.externalMediaDirs
            if (external.size > 1) {
                filePath = external[1].absolutePath
                filePath = filePath.substring(0, filePath.indexOf("Android")) + relativePath
            }
            filePath
        }
    }

    fun createProgressDialog(context: Context, title: Int): AlertDialog {
        return MaterialAlertDialogBuilder(context, R.style.DialogStyle).apply {
            setTitle(title)
            setCancelable(false)
            View.inflate(context, R.layout.dialog_download_progress, null).apply {
                icon.visibility = View.GONE
                downloadProgress.isIndeterminate = true
                setView(this)
            }
        }.show()
    }
}
