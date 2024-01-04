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
package com.infomaniak.drive.data.services

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ProgressResponseBody
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.extensions.RemoteFileException
import com.infomaniak.drive.extensions.getFileFromRemote
import com.infomaniak.drive.extensions.letAll
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MediaUtils
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.downloadProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File as IOFile

class BulkDownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private var notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)
    private var filesPair: MutableMap<Int, Pair<File?, IOFile?>> = mutableMapOf()

    private val fileIds: IntArray by lazy { inputData.getIntArray(FILE_IDS) ?: intArrayOf() }
    private val userDrive: UserDrive by lazy {
        UserDrive(
            userId = inputData.getInt(USER_ID, AccountUtils.currentUserId),
            driveId = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        )
    }
    private val filesCount by lazy { fileIds.size }
    private val downloadProgressNotification by lazy { createDownloadNotification() }

    private var downloadComplete = 0
    private var currentDownloadFileName: String = ""
    private var lastUpdateProgressMillis = System.currentTimeMillis()

    override suspend fun doWork(): Result {
        return runCatching {
            SentryLog.i(TAG, "Work started")
            initOfflineDownload()
        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> {
                    clearFiles()
                    notifyDownloadCancelled()
                    Result.failure()
                }
                is UploadTask.NetworkException -> {
                    Result.failure()
                }
                is RemoteFileException -> {
                    Sentry.captureException(exception)
                    Result.failure()
                }
                else -> {
                    SentryLog.e(TAG, "Failure", exception)
                    Result.failure()
                }
            }
        }.also {
            notificationManagerCompat.cancel(NOTIFICATION_ID)
        }
    }

    private fun clearFiles() {
        for ((_, value) in filesPair.entries) {
            Pair(value.first, value.second).letAll { file, offlineFile ->
                if (offlineFile.exists() && !file.isIntactFile(offlineFile)) offlineFile.delete()
            }
        }
    }

    private suspend fun initOfflineDownload(): Result {
        var result = Result.failure()
        fileIds.forEach { fileId ->
            val file = FileController.getFileById(fileId, userDrive)
            val offlineFile = file?.getOfflineFile(applicationContext, userDrive.userId)
            val cacheFile = file?.getCacheFile(applicationContext, userDrive)

            offlineFile?.let {
                if (file.isOfflineAndIntact(it)) {
                    result = Result.success()
                    return@forEach
                }
            }

            setProgress(workDataOf(PROGRESS to 0, FILE_ID to fileId))

            if (offlineFile?.exists() == true) offlineFile.delete()
            if (cacheFile?.exists() == true) cacheFile.delete()

            if (file == null || offlineFile == null) {
                getFileFromRemote(fileId, userDrive) { downloadedFile ->
                    filesPair[downloadedFile.id] =
                        Pair(downloadedFile, downloadedFile.getOfflineFile(applicationContext, userDrive.driveId))
                }
            } else {
                filesPair[file.id] = Pair(file, offlineFile)
            }

            result = file?.let {
                currentDownloadFileName = file.name
                startOfflineDownload(file = file, offlineFile = offlineFile!!)
            } ?: Result.failure()
        }

        if (result == Result.success()) {
            applicationContext.cancelNotification(NOTIFICATION_ID)
        }

        return result
    }

    private fun createDownloadNotification(): NotificationCompat.Builder {
        val cancelPendingIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val cancelAction = NotificationCompat.Action(
            /* icon = */ null,
            /* title = */ applicationContext.getString(R.string.buttonCancel),
            /* intent = */ cancelPendingIntent
        )
        return applicationContext.downloadProgressNotification().apply {
            setOngoing(true)
            setContentTitle("Import in progress")
            addAction(cancelAction)
        }
    }

    private fun updateDownloadNotification(contentTitle: String, contentText: String, progressPercent: Int) {
        downloadProgressNotification.apply {
            setContentTitle(contentTitle)
            setContentText(contentText)
            setProgress(100, progressPercent, false)
            notificationManagerCompat.notifyCompat(applicationContext, NOTIFICATION_ID, build())
        }
    }

    private suspend fun startOfflineDownload(file: File, offlineFile: IOFile): Result = withContext(Dispatchers.Default) {
        val lastUpdate = workDataOf(PROGRESS to 100, FILE_ID to file.id)
        val okHttpClient = AccountUtils.getHttpClient(userDrive.userId, null)
        val response = downloadFileResponse(
            fileUrl = ApiRoutes.downloadFile(file),
            okHttpClient = okHttpClient
        ) { progress ->
            if (!isActive) {
                notificationManagerCompat.cancel(NOTIFICATION_ID)
                throw CancellationException()
            }
            launch(Dispatchers.Main) { setProgress(workDataOf(PROGRESS to progress, FILE_ID to file.id)) }
            SentryLog.d(TAG, "download $progress%")
        }

        saveRemoteData(response, offlineFile) {
            launch(Dispatchers.Main) { setProgress(lastUpdate) }
            FileController.updateOfflineStatus(file.id, true)
            offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
            if (file.isMedia()) MediaUtils.scanFile(applicationContext, offlineFile)
        }

        if (response.isSuccessful) {
            fileDownloaded()
            Result.success()
        } else Result.failure()
    }

    private fun fileDownloaded() {
        lastUpdateProgressMillis = System.currentTimeMillis()
        downloadComplete += 1

        val progressPercent = (downloadComplete * 100) / filesCount
        updateDownloadNotification(
            contentTitle = "Import in progress ($progressPercent%)",
            contentText = "$downloadComplete files downloaded out of $filesCount",
            progressPercent = progressPercent
        )
    }

    private fun notifyDownloadCancelled() {
        Intent().apply {
            action = TAG
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(this)
        }
    }

    private fun saveRemoteData(
        response: Response,
        outputFile: IOFile? = null,
        outputStream: AutoCloseOutputStream? = null,
        onFinish: (() -> Unit)? = null
    ) {
        SentryLog.d(TAG, "save remote data to ${outputFile?.path}")
        BufferedInputStream(response.body?.byteStream()).use { input ->
            val stream = outputStream ?: outputFile?.outputStream()
            stream?.use { output ->
                input.copyTo(output)
                onFinish?.invoke()
            }
        }
    }

    private fun downloadFileResponse(
        fileUrl: String,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
        onProgress: (progress: Int) -> Unit
    ): Response {
        val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()

        return okHttpClient.newBuilder()
            .addNetworkInterceptor(downloadProgressInterceptor(onProgress)).build()
            .newCall(request).execute()
    }

    private fun downloadProgressInterceptor(onProgress: (progress: Int) -> Unit) = Interceptor { chain: Interceptor.Chain ->
        val originalResponse = chain.proceed(chain.request())

        originalResponse.newBuilder()
            .body(ProgressResponseBody(originalResponse.body!!, object : ProgressResponseBody.ProgressListener {
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                    val currentSystemTimeMillis = System.currentTimeMillis()
                    if (currentSystemTimeMillis - lastUpdateProgressMillis > MAX_INTERVAL_BETWEEN_PROGRESS_UPDATE_MS) {
                        lastUpdateProgressMillis = currentSystemTimeMillis
                        val progress = (bytesRead.toFloat() / contentLength.toFloat() * 100F).toInt()
                        onProgress(progress)
                    }
                }
            })).build()
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(0, downloadProgressNotification.build())

    companion object {
        const val TAG = "BulkDownloadWorker"
        const val DRIVE_ID = "drive_id"
        const val FILE_IDS = "file_ids"
        const val FILE_ID = "file_id"
        const val PROGRESS = "progress"
        const val USER_ID = "user_id"

        private const val NOTIFICATION_ID = 0
        private const val MAX_INTERVAL_BETWEEN_PROGRESS_UPDATE_MS = 1000L
    }
}
