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

import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.CountDownTimer
import android.view.View
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.data.sync.UploadAdapter
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment
import kotlinx.android.synthetic.main.dialog_name_prompt.view.*
import java.util.*
import kotlin.math.pow


object Utils {

    const val ROOT_ID = 1
    const val OTHER_ROOT_ID = -1

    fun confirmFileDeletion(
        context: Context,
        fileName: String?,
        fromTrash: Boolean = false,
        deletionCount: Int = 1,
        onPositiveButtonClicked: (dialog: Dialog) -> Unit
    ) {
        val title: Int
        val message: String
        val button: Int
        if (fromTrash) {
            title = R.string.modalDeleteTitle
            message = context.resources.getQuantityString(
                R.plurals.modalDeleteDescription,
                deletionCount,
                fileName ?: deletionCount
            )
            button = R.string.buttonDelete
        } else {
            title = R.string.modalMoveTrashTitle
            message = context.resources.getQuantityString(
                R.plurals.modalMoveTrashDescription,
                deletionCount,
                fileName ?: deletionCount
            )
            button = R.string.buttonMove
        }
        val dialog = MaterialAlertDialogBuilder(context, R.style.DeleteDialogStyle)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(button) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onPositiveButtonClicked(dialog)
            it.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
    }

    fun createConfirmation(
        context: Context,
        title: String,
        message: String? = null,
        autoDismiss: Boolean = true,
        isDeletion: Boolean = false,
        onConfirmation: (dialog: Dialog) -> Unit
    ) {
        val style = if (isDeletion) R.style.DeleteDialogStyle else R.style.DialogStyle
        val dialog = MaterialAlertDialogBuilder(context, style)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.buttonConfirm) { _, _ -> }
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

    fun createRefreshTimer(milliseconds: Long = 600, onTimerFinish: () -> Unit): CountDownTimer {
        return object : CountDownTimer(milliseconds, milliseconds) {
            override fun onTick(millisUntilFinished: Long) = Unit
            override fun onFinish() {
                onTimerFinish()
            }
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
            putExtra(SelectFolderActivity.DISABLE_SELECTED_FOLDER, currentFolder)
        }
        ownerFragment.startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
    }

    fun Context.openWith(file: File, userDrive: UserDrive = UserDrive()) {
        try {
            startActivity(openWithIntent(file, userDrive))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.allActivityNotFoundError, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun Context.openWithIntent(file: File, userDrive: UserDrive = UserDrive()): Intent {
        return Intent().apply {
            val uri = CloudStorageProvider.createShareFileUri(this@openWithIntent, file, userDrive)!!
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, contentResolver.getType(uri))
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
        val folder = java.io.File(context.cacheDir, UploadAdapter.UPLOAD_FOLDER).apply { if (!exists()) mkdirs() }
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
}
