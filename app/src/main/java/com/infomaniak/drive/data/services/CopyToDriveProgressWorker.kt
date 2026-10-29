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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.ImportProgress
import com.infomaniak.drive.data.models.MqttAction
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.LaunchActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.ForegroundInfoExt
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.copyToDriveProgressNotification
import com.infomaniak.drive.utils.copyToDriveResultMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CopyToDriveProgressWorker(context: Context, workerParams: WorkerParameters) : ListenableWorker(context, workerParams) {

    private val resultNotificationId: Int = UUID.randomUUID().hashCode()
    private val notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)
    private val notificationBuilder: NotificationCompat.Builder by lazy {
        applicationContext.copyToDriveProgressNotification().apply {
            setContentTitle(applicationContext.getString(R.string.copyToDriveStarted, fileName))
        }
    }
    private val isFinished = AtomicBoolean(false)

    private var importId: Int = 0
    private var fileName: String = ""
    private var destDriveId: Int = 0
    private var destFolderId: Int = 0
    private var mqttNotificationsObserver: Observer<MqttNotification>? = null
    private lateinit var timer: CountDownTimer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var userId: Int = -1
    private var isReconciling = false

    override fun startWork(): ListenableFuture<Result> {
        importId = inputData.getInt(IMPORT_ID_KEY, 0)
        fileName = inputData.getString(FILE_NAME_KEY).orEmpty()
        destDriveId = inputData.getInt(DEST_DRIVE_ID_KEY, -1)
        destFolderId = inputData.getInt(DEST_FOLDER_ID_KEY, -1)
        userId = inputData.getInt(USER_ID_KEY, AccountUtils.currentUserId)

        val notification = buildNotification(progress = null).apply {
            if (SDK_INT >= 31) foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
        }.build()

        val foregroundInfo = ForegroundInfoExt.build(importId, notification) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        setForegroundAsync(foregroundInfo)

        SentryLog.i("CopyToDrive", "stage=tracking_started")

        return CallbackToFutureAdapter.getFuture { completer ->
            timer = createRefreshTimer(milliseconds = COPY_TO_DRIVE_TIMEOUT) {
                if (!isReconciling && !isFinished.get()) {
                    isReconciling = true
                    scope.launch {
                        SentryLog.i("CopyToDrive", "stage=reconciliation_started reason=inactivity_timeout")
                        val client = AccountUtils.getHttpClient(userId).newBuilder().cache(null).build()
                        val outcome = reconcileCopyToDrive(importId) { page ->
                            ApiRepository.getExternalImports(destDriveId, page, client)
                        }
                        finish(completer, outcome, "server_reconciliation")
                    }
                }
            }
            timer.start()

            launchObserver { isSuccess, reason ->
                finish(completer, if (isSuccess) CopyToDriveOutcome.SUCCESS else CopyToDriveOutcome.FAILURE, reason)
            }
            if (!isFinished.get()) MqttClientWrapper.start(externalImportId = importId)
        }
    }

    private fun finish(completer: CallbackToFutureAdapter.Completer<Result>, outcome: CopyToDriveOutcome, reason: String) {
        if (!isFinished.compareAndSet(false, true)) return
        SentryLog.i("CopyToDrive", "stage=tracking_finished outcome=${outcome.name} reason=$reason")
        releaseResources()
        showResultNotification(outcome)
        // WorkManager success means monitoring finished, not that the remote transfer succeeded.
        completer.set(Result.success())
    }

    override fun onStopped() {
        super.onStopped()
        isFinished.set(true)
        SentryLog.i("CopyToDrive", "stage=tracking_stopped")
        releaseResources()
    }

    private fun releaseResources() {
        scope.cancel()
        if (::timer.isInitialized) timer.cancel()
        mqttNotificationsObserver?.let { MqttClientWrapper.removeObserver(it) }
        mqttNotificationsObserver = null
        if (importId > 0) MqttClientWrapper.stopExternalImportTracking(importId)
        notificationManagerCompat.cancel(importId)
    }

    private fun launchObserver(onOperationFinished: (isSuccess: Boolean, reason: String) -> Unit) {
        mqttNotificationsObserver = Observer { notification ->
            if (isFinished.get()) return@Observer
            if (notification.importId != null) {
                SentryLog.i("CopyToDrive", "stage=tracking_event import_matches=${notification.importId == importId}")
            }
            if (notification.importId != importId) return@Observer

            restartTimeout()
            val terminalResult = terminalImportResult(notification, importId)
            if (terminalResult != null) {
                onOperationFinished(terminalResult, notification.action!!.name)
            } else {
                notification.importProgress?.let { progress ->
                    notificationManagerCompat.notifyCompat(importId, buildNotification(progress))
                }
            }
        }

        MqttClientWrapper.observeForever(mqttNotificationsObserver!!)
    }

    private fun restartTimeout() {
        if (isReconciling) return
        timer.cancel()
        timer.start()
    }


    private fun showResultNotification(outcome: CopyToDriveOutcome) {
        val description = when (outcome) {
            CopyToDriveOutcome.UNKNOWN -> applicationContext.getString(R.string.copyToDriveStatusUnknown)
            else -> applicationContext.copyToDriveResultMessage(outcome == CopyToDriveOutcome.SUCCESS, fileName)
        }
        val builder = applicationContext.buildGeneralNotification(
            title = applicationContext.getString(R.string.buttonCopyToDrive),
            description = description,
        ).apply {
            setAutoCancel(true)
            if (outcome != CopyToDriveOutcome.FAILURE) setContentIntent(destinationPendingIntent())
        }
        notificationManagerCompat.notifyCompat(resultNotificationId, builder)
    }

    private fun destinationPendingIntent(): PendingIntent {
        val args = LaunchActivityArgs(
            destinationUserId = userId,
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
            setContentText(progress?.let { "${it.percent}%" })
            setProgress(100, progress?.percent ?: 0, progress == null || progress.totalFiles == 0)
        }
    }

    companion object {
        internal fun terminalImportResult(notification: MqttNotification, importId: Int): Boolean? {
            if (notification.importId != importId) return null
            return when (notification.action) {
                MqttAction.EXTERNAL_IMPORT_FINISHED -> true
                MqttAction.EXTERNAL_IMPORT_ERROR, MqttAction.EXTERNAL_IMPORT_CANCELED -> false
                else -> null
            }
        }

        private const val IMPORT_ID_KEY = "import_id_key"
        private const val FILE_NAME_KEY = "file_name_key"
        private const val DEST_DRIVE_ID_KEY = "dest_drive_id_key"
        private const val DEST_FOLDER_ID_KEY = "dest_folder_id_key"
        private const val USER_ID_KEY = "user_id_key"
        private const val COPY_TO_DRIVE_TIMEOUT = 30_000L

        fun scheduleWork(context: Context, importId: Int, fileName: String, destDriveId: Int, destFolderId: Int) {
            if (importId <= 0 || fileName.isBlank()) return

            val workRequest = OneTimeWorkRequestBuilder<CopyToDriveProgressWorker>()
                .setInputData(
                    workDataOf(
                        IMPORT_ID_KEY to importId,
                        FILE_NAME_KEY to fileName,
                        DEST_DRIVE_ID_KEY to destDriveId,
                        DEST_FOLDER_ID_KEY to destFolderId,
                        USER_ID_KEY to AccountUtils.currentUserId,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
