/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build.VERSION.SDK_INT
import android.os.CountDownTimer
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.core.legacy.utils.NotificationUtilsCore.Companion.PENDING_INTENT_FLAGS
import com.infomaniak.core.legacy.utils.Utils.createRefreshTimer
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.core.notifications.notifyCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.ImportProgress
import com.infomaniak.drive.data.models.MqttAction
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.LaunchActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.ForegroundInfoExt
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.copyToDriveProgressNotification
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CopyToDriveProgressWorker(context: Context, workerParams: WorkerParameters) : ListenableWorker(context, workerParams) {

    private var importId: Int = 0
    private var fileName: String = ""
    private var destDriveId: Int = 0
    private var destFolderId: Int = 0
    private var notificationId: Int = 0
    private val resultNotificationId: Int = UUID.randomUUID().hashCode()

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private var mqttNotificationsObserver: Observer<MqttNotification>? = null

    private lateinit var timer: CountDownTimer
    private lateinit var lastReception: Date
    private val isFinished = AtomicBoolean(false)

    override fun startWork(): ListenableFuture<Result> {
        importId = inputData.getInt(IMPORT_ID_KEY, 0)
        fileName = inputData.getString(FILE_NAME_KEY).orEmpty()
        destDriveId = inputData.getInt(DEST_DRIVE_ID_KEY, -1)
        destFolderId = inputData.getInt(DEST_FOLDER_ID_KEY, -1)

        if (importId <= 0 || fileName.isBlank()) {
            return CallbackToFutureAdapter.getFuture { completer -> completer.set(Result.failure()) }
        }

        notificationId = importId
        notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

        notificationBuilder = applicationContext.copyToDriveProgressNotification().apply {
            setContentTitle(applicationContext.getString(R.string.copyToDriveStarted, fileName))
        }

        val notification = buildNotification(progress = null).apply {
            if (SDK_INT >= 31) foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
        }.build()

        val foregroundInfo = ForegroundInfoExt.build(notificationId, notification) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        setForegroundAsync(foregroundInfo)

        MqttClientWrapper.start(externalImportId = importId)
        lastReception = Date()

        return CallbackToFutureAdapter.getFuture { completer ->
            timer = createRefreshTimer(milliseconds = 1_000L) {
                if (Date().time - lastReception.time > COPY_TO_DRIVE_TIMEOUT) finish(completer, isSuccess = null) else timer.start()
            }
            timer.start()

            launchObserver { isSuccess -> finish(completer, isSuccess) }
        }
    }

    private fun finish(completer: CallbackToFutureAdapter.Completer<Result>, isSuccess: Boolean?) {
        if (!isFinished.compareAndSet(false, true)) return
        if (::timer.isInitialized) timer.cancel()
        mqttNotificationsObserver?.let { MqttClientWrapper.removeObserver(it) }
        mqttNotificationsObserver = null

        if (isSuccess == null) MqttClientWrapper.stopExternalImportTracking(importId)

        notificationManagerCompat.cancel(notificationId)
        isSuccess?.let { showResultNotification(it) }
        completer.set(Result.success())
    }

    private fun launchObserver(onTerminal: (isSuccess: Boolean?) -> Unit) {
        mqttNotificationsObserver = Observer { notification ->
            if (notification.importId != importId) return@Observer

            lastReception = Date()
            when (notification.action) {
                MqttAction.EXTERNAL_IMPORT_FINISHED -> onTerminal(true)
                MqttAction.EXTERNAL_IMPORT_ERROR -> onTerminal(false)
                MqttAction.EXTERNAL_IMPORT_CANCELED -> onTerminal(null)
                else -> notification.importProgress?.let { progress ->
                    notificationManagerCompat.notifyCompat(notificationId, buildNotification(progress))
                }
            }
        }

        MqttClientWrapper.observeForever(mqttNotificationsObserver!!)
    }

    private fun showResultNotification(isSuccess: Boolean) {
        val description = if (isSuccess) {
            applicationContext.getString(R.string.copyToDriveSuccess, fileName)
        } else {
            applicationContext.getString(R.string.errorCopyToDrive)
        }
        val builder = applicationContext.buildGeneralNotification(
            title = applicationContext.getString(R.string.buttonCopyToDrive),
            description = description,
        ).apply {
            setAutoCancel(true)
            if (isSuccess) setContentIntent(destinationPendingIntent())
        }
        notificationManagerCompat.notifyCompat(resultNotificationId, builder)
    }

    private fun destinationPendingIntent(): PendingIntent {
        val destUserId = DriveInfosController.getDrive(driveId = destDriveId)?.userId ?: AccountUtils.currentUserId
        val args = LaunchActivityArgs(
            destinationUserId = destUserId,
            destinationDriveId = destDriveId,
            destinationRemoteFolderId = destFolderId,
        )
        val intent = Intent(applicationContext, LaunchActivity::class.java).apply {
            clearStack()
            putExtras(args.toBundle())
        }
        return PendingIntent.getActivity(applicationContext, resultNotificationId, intent, PENDING_INTENT_FLAGS)
    }

    private fun buildNotification(progress: ImportProgress?): NotificationCompat.Builder {
        return notificationBuilder.apply {
            val isIndeterminate = progress == null || !progress.isDeterminate
            setContentText(progress?.let { "${it.percent}%" })
            setProgress(100, progress?.percent ?: 0, isIndeterminate)
        }
    }

    companion object {
        const val TAG = "copy_to_drive_progress_worker"
        const val IMPORT_ID_KEY = "import_id_key"
        const val FILE_NAME_KEY = "file_name_key"
        const val DEST_DRIVE_ID_KEY = "dest_drive_id_key"
        const val DEST_FOLDER_ID_KEY = "dest_folder_id_key"
        private const val COPY_TO_DRIVE_TIMEOUT = 30_000L

        fun scheduleWork(context: Context, importId: Int, fileName: String, destDriveId: Int, destFolderId: Int) {
            val workRequest = OneTimeWorkRequestBuilder<CopyToDriveProgressWorker>()
                .setInputData(
                    workDataOf(
                        IMPORT_ID_KEY to importId,
                        FILE_NAME_KEY to fileName,
                        DEST_DRIVE_ID_KEY to destDriveId,
                        DEST_FOLDER_ID_KEY to destFolderId,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
