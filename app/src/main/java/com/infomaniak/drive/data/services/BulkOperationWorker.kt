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
package com.infomaniak.drive.data.services

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build.VERSION.SDK_INT
import android.os.CountDownTimer
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.core.legacy.utils.Utils.createRefreshTimer
import com.infomaniak.core.notifications.notifyCompat
import com.infomaniak.drive.data.models.ActionProgress
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.utils.ForegroundInfoExt
import java.util.Date
import java.util.UUID

class BulkOperationWorker(context: Context, workerParams: WorkerParameters) : ListenableWorker(context, workerParams) {

    private lateinit var actionUuid: String
    private lateinit var bulkOperationNotification: NotificationCompat.Builder
    private lateinit var bulkOperationType: BulkOperationType
    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private var mqttNotificationsObserver: Observer<MqttNotification>? = null

    private lateinit var timer: CountDownTimer
    private lateinit var lastReception: Date

    private var notificationId: Int = UUID.randomUUID().hashCode()
    private var totalFiles: Int = 0

    override fun startWork(): ListenableFuture<Result> {
        actionUuid = inputData.getString(ACTION_UUID).toString()
        bulkOperationType = BulkOperationType.valueOf(inputData.getString(OPERATION_TYPE_KEY).toString())
        notificationId = actionUuid.hashCode()
        notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        totalFiles = inputData.getInt(TOTAL_FILES_KEY, 0)

        bulkOperationNotification = bulkOperationType.getNotificationBuilder(applicationContext).apply {
            setContentTitle(applicationContext.getString(bulkOperationType.title, 0, totalFiles))
        }

        val notification = createNotificationBuilder().apply {
            if (SDK_INT >= 31) {
                foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
            }
        }.build()
        val foregroundInfo =
            ForegroundInfoExt.build(notificationId, notification) { ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC }
        setForegroundAsync(foregroundInfo)
        lastReception = Date()

        return CallbackToFutureAdapter.getFuture { completer ->
            timer = createRefreshTimer(milliseconds = 1_000L) {
                if (Date().time - lastReception.time > BULK_OPERATION_TIMEOUT) onFinish(completer) else timer.start()
            }
            timer.start()

            launchObserver { onFinish(completer) }
        }
    }

    private fun onFinish(completer: CallbackToFutureAdapter.Completer<Result>) {
        mqttNotificationsObserver?.let { MqttClientWrapper.removeObserver(it) }
        completer.set(Result.success())
    }

    private fun launchObserver(onOperationFinished: () -> Unit) {
        mqttNotificationsObserver = Observer { notification ->
            if (notification.isProgressNotification() && notification.actionUuid == actionUuid) {
                lastReception = Date()
                if (notification.progress!!.todo == 0) {
                    onOperationFinished()
                } else {
                    notificationManagerCompat.notifyCompat(
                        notificationId = notificationId,
                        builder = createNotificationBuilder(notification.progress)
                    )
                }
            }
        }

        MqttClientWrapper.observeForever(mqttNotificationsObserver!!)
    }

    private fun createNotificationBuilder(progress: ActionProgress? = null): NotificationCompat.Builder {
        return bulkOperationNotification.apply {
            val progressValue = progress?.success ?: 0
            val contentTitle = applicationContext.getString(bulkOperationType.title, progressValue, totalFiles)
            setContentTitle(contentTitle)
            setContentText("${progress?.percent ?: 0}%")
            setProgress(100, progress?.percent ?: 0, progress == null)
        }
    }

    companion object {
        const val TAG = "bulk_operation_worker"

        const val ACTION_UUID = "action_uuid"
        const val TOTAL_FILES_KEY = "total_files"
        const val OPERATION_TYPE_KEY = "operation_type_key"
        const val BULK_OPERATION_TIMEOUT = 30_000L
    }
}
