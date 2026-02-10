/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.core.legacy.utils.NotificationUtilsCore.Companion.PENDING_INTENT_FLAGS
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.core.notifications.notifyCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.LaunchActivityArgs
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.menu.settings.SyncSettingsActivity
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.UPLOAD_SERVICE_ID
import com.infomaniak.drive.utils.NotificationUtils.uploadNotification
import java.util.UUID

object UploadNotifications {

    const val NOTIFICATION_FILES_LIMIT = 5

    fun getCurrentUploadNotification(context: Context, pendingCount: Int): NotificationCompat.Builder {
        val pendingTitle = context.getString(R.string.uploadInProgressTitle)
        val pendingDescription = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingCount,
            pendingCount
        )
        val intent = Intent(context, LaunchActivity::class.java).clearStack()
        val contentIntent = PendingIntent.getActivity(context, UPLOAD_SERVICE_ID, intent, PENDING_INTENT_FLAGS)
        return getNotificationBuilder(context, pendingTitle, pendingDescription, contentIntent)
    }

    fun setupCurrentUploadNotification(context: Context, pendingCount: Int) {
        val pendingTitle = context.getString(R.string.uploadInProgressTitle)
        val pendingDescription = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingCount,
            pendingCount
        )
        val intent = Intent(context, LaunchActivity::class.java).clearStack()
        val contentIntent = PendingIntent.getActivity(context, UPLOAD_SERVICE_ID, intent, PENDING_INTENT_FLAGS)
        showNotification(context, pendingTitle, pendingDescription, UPLOAD_SERVICE_ID, contentIntent)
    }

    fun UploadFile.networkErrorNotification(context: Context) {
        uploadInterruptedNotification(
            context = context,
            titleRes = R.string.uploadNetworkErrorTitle,
            messageRes = R.string.uploadNetworkErrorDescription
        )
    }

    fun UploadFile.folderNotFoundNotification(context: Context) {

        val description: Int
        val contentIntent: PendingIntent?

        if (isSync()) {
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

    fun UploadFile.productMaintenanceExceptionNotification(context: Context, isTechnicalMaintenance: Boolean) {
        val drive = DriveInfosController.getDrive(userId = userId, driveId = driveId)
        val title = if (isTechnicalMaintenance) R.plurals.driveMaintenanceTitle else R.plurals.driveBlockedTitle
        val description = context.resources.getQuantityString(title, 1, drive?.name)
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = description,
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.foregroundServiceQuotaNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadPausedTitle),
            description = context.getString(R.string.uploadPausedDescription),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context),
        )
    }

    fun permissionErrorNotification(context: Context) {
        val mainActivityIntent = PendingIntent.getActivity(
            context, NotificationUtils.UPLOAD_STATUS_ID,
            Intent(context, MainActivity::class.java).clearStack(), PENDING_INTENT_FLAGS
        )
        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.uploadPermissionError),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = mainActivityIntent
        )
    }

    fun UploadFile.exceptionNotification(context: Context, isLimitExceeded: Boolean = false) {
        uploadInterruptedNotification(
            context = context,
            titleRes = R.string.uploadErrorTitle,
            messageRes = if (isLimitExceeded) R.string.errorFilesLimitExceeded else R.string.anErrorHasOccurred
        )
    }

    fun UploadFile.showUploadedFilesNotification(
        context: Context,
        successCount: Int,
        successNames: Collection<String>,
        failedCount: Int,
        failedNames: Collection<String>
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
                val description = getQuantityString(R.plurals.allUploadFinishedDescription, successCount, successCount)
                StringBuilder().apply {
                    appendLine(description)
                    for (file in successNames.take(NOTIFICATION_FILES_LIMIT)) appendLine(file)
                    val otherCount = successCount - NOTIFICATION_FILES_LIMIT
                    if (otherCount > 0) appendLine(getQuantityString(R.plurals.uploadImportedOtherAmount, otherCount, otherCount))
                }.toString()
            }
        }

        val titleResId = if (successCount > 0) R.string.allUploadFinishedTitle else R.string.uploadErrorTitle

        showNotification(
            context = context,
            title = context.getString(titleResId),
            description = description,
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context),
            locateButton = true,
        )
    }

    fun getDestinationFolderId(resources: UploadFile): Int {
        val matches = resources.remoteSubFolder?.split("/")
        var folderId: Int = resources.remoteFolder
        if (matches != null) {
            for (match in matches) {
                val childrenId = FileController.getIdOfChildrenFileWithName(folderId, match)
                if (childrenId.isNotEmpty()) {
                    folderId = childrenId.first()
                } else {
                    return folderId
                }
            }
        }
        return folderId
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
        locateButton: Boolean = false
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val notificationBuilder = getNotificationBuilder(context, title, description, contentIntent, locateButton)
        notificationManagerCompat.notifyCompat(notificationId, notificationBuilder)
    }

    private fun getNotificationBuilder(
        context: Context,
        title: String,
        description: String,
        contentIntent: PendingIntent? = null,
        locateButton: Boolean = false
    ): NotificationCompat.Builder {
        return context.uploadNotification().apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))
            setContentIntent(contentIntent)
            if (locateButton) {
                addAction(
                    NotificationCompat.Action(R.drawable.ic_export, context.getString(R.string.locateButton), contentIntent)
                )
            }
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

    fun UploadFile.progressPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LaunchActivity::class.java).clearStack().apply {
            putExtras(
                LaunchActivityArgs(
                    destinationUserId = userId,
                    destinationDriveId = driveId,
                    destinationRemoteFolderId = getDestinationFolderId(this@progressPendingIntent)
                ).toBundle()
            )
        }

        return PendingIntent.getActivity(context, NotificationUtils.UPLOAD_STATUS_ID, intent, PENDING_INTENT_FLAGS)
    }

    fun Context.syncSettingsActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            /* context = */ this,
            /* requestCode = */ UUID.randomUUID().hashCode(),
            /* intent = */ Intent(this, SyncSettingsActivity::class.java).clearStack(),
            /* flags = */ PENDING_INTENT_FLAGS,
        )
    }
}
