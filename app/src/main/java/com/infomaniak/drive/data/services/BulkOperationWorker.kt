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
import com.infomaniak.drive.data.models.Notification
import com.infomaniak.drive.utils.NotificationUtils.moveOperationProgressNotification

class BulkOperationWorker(private val context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {

    private lateinit var currentFolder: String
    private lateinit var destinationFolder: String
    private lateinit var mqttNotificationsObserver: Observer<Notification>
    private var fileCount = 0

    private fun launchObserver(callback: Callback) {
        mqttNotificationsObserver = Observer<Notification> { notification ->
            if (notification is ActionProgressNotification) {
                if (notification.progress.percent == 100) {
                    callback.onSuccess()
                } else {
                    setForegroundAsync(
                        createForegroundInfo(
                            currentFolder,
                            destinationFolder,
                            fileCount,
                            notification.progress.percent
                        )
                    )
                }
            }
        }

        MqttClientWrapper.observeForever(mqttNotificationsObserver)
    }

    private fun createForegroundInfo(
        currentFolder: String,
        destinationFolder: String,
        fileCount: Int,
        progress: Int
    ): ForegroundInfo {
        val notificationBuilder = context.moveOperationProgressNotification().apply {
            setContentTitle("Déplacement en cours")
            setContentText("De $currentFolder vers $destinationFolder")
            setSubText("$fileCount fichiers à déplacer")
            setProgress(100, progress, progress == 0)
        }

        return ForegroundInfo(5, notificationBuilder.build())
    }

    override fun startWork(): ListenableFuture<Result> {
        currentFolder = inputData.getString("currentFolderName").toString()
        destinationFolder = inputData.getString("destinationFolderName").toString()
        fileCount = inputData.getInt("fileCount", 0)

        setForegroundAsync(createForegroundInfo(currentFolder, destinationFolder, fileCount, 0))

        val futureCallback = CallbackToFutureAdapter.getFuture<Result> { completer ->
            val callback: Callback = object : Callback {
                override fun onFailure() {
                    MqttClientWrapper.removeObserver(mqttNotificationsObserver)
                    completer.set(Result.failure())
                }

                override fun onSuccess() {
                    MqttClientWrapper.removeObserver(mqttNotificationsObserver)
                    completer.set(Result.success())
                }
            }
            launchObserver(callback)
            callback
        }


        return futureCallback
    }

    interface Callback {
        fun onFailure()
        fun onSuccess()
    }
}