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
package com.infomaniak.drive.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.menu.settings.SyncSettingsActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.uploadNotification
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import io.sentry.Sentry

object UploadNotifications {

    fun UploadFile.setupCurrentUploadNotification(context: Context, pendingCount: Int) {
        val pendingTitle = context.getString(R.string.uploadInProgressTitle)
        val pendingDescription = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingCount,
            pendingCount
        )
        val contentIntent = progressPendingIntent(context)
        showNotification(context, pendingTitle, pendingDescription, NotificationUtils.UPLOAD_STATUS_ID, contentIntent)
    }

    fun UploadFile.networkErrorNotification(context: Context, wifiRequired: Boolean = false) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadNetworkErrorTitle),
            description = if (wifiRequired) context.getString(R.string.uploadNetworkErrorWifiRequired) else context.getString(R.string.uploadNetworkErrorDescription),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.folderNotFoundNotification(context: Context) {
        UploadFile.deleteAllByFolderId(remoteFolder)
        val isSyncFile = type == UploadFile.Type.SYNC.name
        if (isSyncFile) {
            Sentry.captureMessage("FolderNotFoundNotification: disableAutoSync")
            context.disableAutoSync()
        }

        val contentIntent = if (isSyncFile) PendingIntent.getActivity(
            context, 0,
            Intent(context, SyncSettingsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
        ) else null

        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.uploadFolderNotFoundError),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = contentIntent
        )
    }

    fun UploadFile.quotaExceededNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.notEnoughStorageDescription1),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.interruptedNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.anErrorHasOccurred),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.outOfMemoryNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.uploadOutOfMemoryError),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.lockErrorNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.errorFileLocked),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.exceptionNotification(context: Context) {
        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.anErrorHasOccurred),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
        )
    }

    fun UploadFile.showUploadedFilesNotification(context: Context, uploadedFilesCount: Int) {
        showNotification(
            context = context,
            title = context.getString(R.string.allUploadFinishedTitle),
            description = context.resources.getQuantityString(
                R.plurals.allUploadFinishedDescription,
                uploadedFilesCount,
                if (uploadedFilesCount == 1) this.fileName else uploadedFilesCount
            ),
            notificationId = NotificationUtils.UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent(context)
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

    fun showNotification(
        context: Context,
        title: String,
        description: String,
        notificationId: Int,
        contentIntent: PendingIntent? = null
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        context.uploadNotification().apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))
            setContentIntent(contentIntent)
            notificationManagerCompat.notify(notificationId, this.build())
        }
    }

    private fun UploadFile.progressPendingIntent(context: Context): PendingIntent? {
        val destination = when (AccountUtils.currentUser) {
            null -> LaunchActivity::class.java
            else -> MainActivity::class.java
        }
        val intent = Intent(context, destination).apply {
            putExtra(MainActivity.INTENT_SHOW_PROGRESS, remoteFolder)
        }
        return PendingIntent.getActivity(
            context, 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}