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
package com.infomaniak.drive.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.menu.settings.SyncSettingsActivity
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.uploadNotification
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.lib.core.utils.clearStack
import io.sentry.Sentry

object UploadNotifications {

    const val NOTIFICATION_FILES_LIMIT = 5

    const val INTENT_DESTINATION_USER_ID = "intent_destination_user_id"
    const val INTENT_DESTINATION_DRIVE_ID = "intent_destination_drive_id"
    const val INTENT_DESTINATION_FOLDER_ID = "intent_folder_id_progress"

    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    fun UploadFile.setupCurrentUploadNotification(context: Context, pendingCount: Int) {
        val pendingTitle = context.getString(R.string.uploadInProgressTitle)
        val pendingDescription = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingCount,
            pendingCount
        )
        val contentIntent = progressPendingIntent(context, isUploadInProgress = true)
        showNotification(context, pendingTitle, pendingDescription, NotificationUtils.UPLOAD_SERVICE_ID, contentIntent)
    }

    fun UploadFile.networkErrorNotification(context: Context) {
        uploadInterruptedNotification(
            context = context,
            titleRes = R.string.uploadNetworkErrorTitle,
            messageRes = R.string.uploadNetworkErrorDescription
        )
    }

    fun UploadFile.folderNotFoundNotification(context: Context) {
        UploadFile.deleteAll(remoteFolder, permanently = true)

        val description: Int
        val contentIntent: PendingIntent?

        if (isSync()) {
            Sentry.captureMessage("FolderNotFoundNotification: disableAutoSync")
            context.disableAutoSync()

            description = R.string.uploadFolderNotFoundSyncDisabledError
            contentIntent = context.syncSettingsActivityPendingIntent()
        } else {
            description = R.string.uploadFolderNotFoundError
            contentIntent = null
        }

        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(description),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = contentIntent
        )
    }

    fun UploadFile.allowedFileSizeExceededNotification(context: Context) {
        uploadInterruptedNotification(context, R.string.uploadFileSizeExceeded)
    }

    fun UploadFile.quotaExceededNotification(context: Context) {
        uploadInterruptedNotification(context, R.string.notEnoughStorageDescription1)
    }

    fun UploadFile.outOfMemoryNotification(context: Context) {
        uploadInterruptedNotification(context, R.string.uploadOutOfMemoryError)
    }

    fun UploadFile.lockErrorNotification(context: Context) {
        uploadInterruptedNotification(context, R.string.errorFileLocked)
    }

    fun permissionErrorNotification(context: Context) {
        val mainActivityIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).clearStack(), pendingIntentFlags
        )
        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.uploadPermissionError),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = mainActivityIntent
        )
    }

    fun UploadFile.exceptionNotification(context: Context) {
        uploadInterruptedNotification(
            context = context,
            titleRes = R.string.uploadErrorTitle,
            messageRes = R.string.anErrorHasOccurred
        )
    }

    fun UploadFile.showUploadedFilesNotification(
        context: Context,
        successCount: Int,
        successNames: MutableList<String>,
        failedCount: Int,
        failedNames: MutableList<String>
    ) = with(context.resources) {
        val total = successCount + failedCount
        val description = when {
            failedCount > 0 -> {
                val failedList = failedNames.joinToString("\n")
                val message = getQuantityString(R.plurals.uploadImportedFailedAmount, failedCount, total, failedCount)
                "$message.\n$failedList"
            }
            successCount == 1 -> getQuantityString(R.plurals.allUploadFinishedDescription, 1, fileName)
            else -> {
                val description = getQuantityString(R.plurals.allUploadFinishedDescription, successCount, successCount.toString())
                StringBuilder().apply {
                    appendLine(description)
                    for (file in successNames.take(NOTIFICATION_FILES_LIMIT)) appendLine(file)
                    val otherCount = successCount - NOTIFICATION_FILES_LIMIT
                    if (otherCount > 0) appendLine(getQuantityString(R.plurals.uploadImportedOtherAmount, otherCount, otherCount))
                }.toString()
            }
        }

        val titleResId = if (successCount > 0) R.string.allUploadFinishedTitle else R.string.uploadErrorTitle

        val pendingIntent = progressPendingIntent(context)
        showNotification(
            context = context,
            title = context.getString(titleResId),
            description = description,
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = pendingIntent,
            actionIntent = pendingIntent
        )
    }

    fun showCancelledByUserNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadCancelTitle),
            description = context.getString(R.string.uploadCancelDescription),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID
        )
    }

    private fun showNotification(
        context: Context,
        title: String,
        description: String,
        notificationId: Int,
        contentIntent: PendingIntent? = null,
        actionIntent: PendingIntent? = null
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        context.uploadNotification().apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))
            setContentIntent(contentIntent)
            actionIntent?.let {
                addAction(NotificationCompat.Action(R.drawable.ic_export, context.getString(R.string.locateButton), it))
            }
            notificationManagerCompat.notify(notificationId, this.build())
        }
    }

    private fun UploadFile.uploadInterruptedNotification(
        context: Context,
        @StringRes messageRes: Int,
        @StringRes titleRes: Int = R.string.uploadInterruptedErrorTitle
    ) {
        showNotification(
            context = context,
            title = context.getString(titleRes),
            description = context.getString(messageRes),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    private fun UploadFile.progressPendingIntent(context: Context, isUploadInProgress: Boolean = false): PendingIntent? {
        val destination = if (isUploadInProgress) MainActivity::class.java else LaunchActivity::class.java
        val intent = Intent(context, destination).clearStack().apply {
            if (!isUploadInProgress) {
                putExtra(INTENT_DESTINATION_USER_ID, userId)
                putExtra(INTENT_DESTINATION_DRIVE_ID, driveId)
            }
            putExtra(INTENT_DESTINATION_FOLDER_ID, remoteFolder)
        }

        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)
    }

    fun Context.syncSettingsActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(this, 0, Intent(this, SyncSettingsActivity::class.java).clearStack(), pendingIntentFlags)
    }
}
