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
package com.infomaniak.drive.utils

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.infomaniak.drive.R

object NotificationUtils {

    const val UPLOAD_SERVICE_ID = 1
    const val UPLOAD_STATUS_ID = 2
    const val CURRENT_UPLOAD_ID = 3
    const val FILE_OBSERVE_ID = 4

    const val ELAPSED_TIME = 500L

    private const val DEFAULT_SMALL_ICON = R.drawable.ic_logo_notification

    fun Context.moveOperationProgressNotification(): NotificationCompat.Builder {
        return progressNotification(getString(R.string.fileListMoveStartedSnackbar), R.drawable.ic_folder_select)
    }

    fun Context.copyOperationProgressNotification(): NotificationCompat.Builder {
        return progressNotification(getString(R.string.fileListCopyStartedSnackbar), R.drawable.ic_copy)
    }

    fun Context.trashOperationProgressNotification(): NotificationCompat.Builder {
        return progressNotification(getString(R.string.fileListDeletionStartedSnackbar), R.drawable.ic_delete)
    }

    fun Context.downloadProgressNotification(): NotificationCompat.Builder {
        return progressNotification(getString(R.string.notificationStartDownloadTicker), android.R.drawable.stat_sys_download)
    }

    fun Context.uploadProgressNotification(): NotificationCompat.Builder {
        return progressNotification(getString(R.string.notificationStartUploadTicker), android.R.drawable.stat_sys_upload)
    }

    private fun Context.progressNotification(ticker: String, icon: Int): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_upload_download)
        return NotificationCompat.Builder(this, channelId).apply {
            setOngoing(true)
            setTicker(ticker)
            setSmallIcon(icon)
            setAutoCancel(true)
            setContentText("0%")
            setOnlyAlertOnce(true)
            setProgress(100, 0, true)
        }
    }

    fun Context.uploadNotification(): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_upload_download)
        return NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(DEFAULT_SMALL_ICON)
        }
    }

    fun Context.uploadServiceNotification(): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_upload_service)
        return NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(DEFAULT_SMALL_ICON)
        }
    }

    fun Context.showGeneralNotification(title: String): NotificationCompat.Builder {
        val channelId = getString(R.string.notification_channel_id_general)
        return NotificationCompat.Builder(this, channelId).apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            setSmallIcon(DEFAULT_SMALL_ICON)
        }
    }

    fun Context.cancelNotification(notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun Context.initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelList = ArrayList<NotificationChannel>()

            val uploadServiceChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_upload_service),
                getString(R.string.notificationUploadServiceChannelName),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
            }
            channelList.add(uploadServiceChannel)

            val uploadDownloadChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_upload_download),
                getString(R.string.notificationUploadDownloadChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
            }
            channelList.add(uploadDownloadChannel)

            val sharedWithMeChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_shared),
                getString(R.string.notificationSharedWithMeChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channelList.add(sharedWithMeChannel)

            val commentChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_comment),
                getString(R.string.notificationCommentChannelName),
                NotificationManager.IMPORTANCE_HIGH
            )
            channelList.add(commentChannel)

            val generalChannel = createNotificationChannel(
                getString(R.string.notification_channel_id_general),
                getString(R.string.notificationGeneralChannelName),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channelList.add(generalChannel)

            val notificationManager = getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(channelList)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Context.createNotificationChannel(
        channelId: String,
        name: String,
        importance: Int
    ): NotificationChannel {
        return NotificationChannel(channelId, name, importance).apply {
            when (importance) {
                NotificationManager.IMPORTANCE_HIGH -> {
                    enableLights(true)
                    setShowBadge(true)
                    lightColor = getColor(R.color.primary)
                }
                else -> {
                    enableLights(false)
                    setShowBadge(false)
                    enableVibration(false)
                }
            }
        }
    }
}