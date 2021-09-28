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
package com.infomaniak.drive.data.services

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.Observer
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.drive.data.models.ActionProgressNotification
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.Notification

class BulkOperationWorker(private val context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {

    private lateinit var actionUuid: String
    private lateinit var bulkOperationType: BulkOperationType
    private var totalFiles: Int = 0
    private var notificationId: Int = 0
    private lateinit var mqttNotificationsObserver: Observer<Notification>

    override fun startWork(): ListenableFuture<Result> {
        actionUuid = inputData.getString(ACTION_UUID).toString()
        notificationId = actionUuid.hashCode()
        totalFiles = inputData.getInt(TOTAL_FILES_KEY, 0)
        bulkOperationType = BulkOperationType.valueOf(inputData.getString(OPERATION_TYPE_KEY)!!)

        createForegroundInfo(notificationId, 0, 0, totalFiles)
        return CallbackToFutureAdapter.getFuture { completer ->
            launchObserver {
                MqttClientWrapper.removeObserver(mqttNotificationsObserver)
                completer.set(Result.success())
            }
        }
    }

    private fun launchObserver(onOperationFinished: (isSuccess: Boolean) -> Unit) {
        mqttNotificationsObserver = Observer<Notification> { notification ->
            if (notification is ActionProgressNotification && notification.actionUuid == actionUuid) {
                if (notification.progress.percent == 100) {
                    onOperationFinished(true)
                } else {
                    setForegroundAsync(
                        createForegroundInfo(
                            notificationId,
                            notification.progress.percent,
                            notification.progress.success,
                            totalFiles
                        )
                    )
                }
            }
        }

        MqttClientWrapper.observeForever(mqttNotificationsObserver)
    }

    private fun createForegroundInfo(operationId: Int, percentage: Int, doneFiles: Int, totalFiles: Int): ForegroundInfo {
        val notificationBuilder = bulkOperationType.getNotificationBuilder(context)!!.apply {
            setContentTitle(context.getString(bulkOperationType.title!!, doneFiles, totalFiles))
            setProgress(100, percentage, doneFiles == 0)
            setContentText("$percentage%")
        }
        return ForegroundInfo(operationId, notificationBuilder.build())
    }

    companion object {
        const val TAG = "bulk_operation_worker"

        const val ACTION_UUID = "action_uuid"
        const val TOTAL_FILES_KEY = "total_files"
        const val OPERATION_TYPE_KEY = "operation_type_key"
    }
}