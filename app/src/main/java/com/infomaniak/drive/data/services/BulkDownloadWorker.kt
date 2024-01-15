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
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadWorkerUtils
import com.infomaniak.drive.utils.MediaUtils
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.NotificationUtils.BULK_DOWNLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import com.infomaniak.drive.utils.RemoteFileException
import com.infomaniak.lib.core.utils.SentryLog
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File as IOFile

class BulkDownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)
    private val filesPair: MutableMap<Int, Pair<File, IOFile>> = mutableMapOf()

    private val fileIds: IntArray by lazy { inputData.getIntArray(FILE_IDS) ?: intArrayOf() }
    private val userDrive: UserDrive by lazy {
        UserDrive(
            userId = inputData.getInt(USER_ID, AccountUtils.currentUserId),
            driveId = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        )
    }
    private val downloadWorkerUtils by lazy { DownloadWorkerUtils() }
    private val filesCount by lazy { fileIds.size }
    private val downloadProgressNotification by lazy {
        downloadWorkerUtils.createDownloadNotification(
            context = context,
            id = id,
            title = context.getString(R.string.bulkDownloadNotificationTitleNoProgress)
        )
    }

    private var numberOfFilesDownloaded = 0
    private var lastUpdateProgressMillis = System.currentTimeMillis()
    private var lastDownloadedFileId = 0

    override suspend fun doWork(): Result {
        return runCatching {
            SentryLog.i(TAG, "Work started")
            initOfflineDownload()
        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> {
                    clearLastDownloadedFile()
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
            notificationManagerCompat.cancel(BULK_DOWNLOAD_ID)
        }
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(BULK_DOWNLOAD_ID, downloadProgressNotification.build())

    private fun clearLastDownloadedFile() {
        with(filesPair[lastDownloadedFileId]?.second) {
            if (this?.exists() == true) delete()
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
                downloadWorkerUtils.getFileFromRemote(applicationContext, fileId, userDrive) { downloadedFile ->
                    lastDownloadedFileId = downloadedFile.id
                    downloadedFile.getOfflineFile(applicationContext, userDrive.driveId)?.let { offlineFile ->
                        filesPair[downloadedFile.id] = Pair(downloadedFile, offlineFile)
                    }
                }
            } else {
                lastDownloadedFileId = file.id
                filesPair[file.id] = Pair(file, offlineFile)
            }

            result = file?.let {
                startOfflineDownload(file = file, offlineFile = offlineFile!!)
            } ?: Result.failure()
        }

        if (result == Result.success()) {
            applicationContext.cancelNotification(BULK_DOWNLOAD_ID)
        }

        return result
    }

    private fun updateDownloadNotification(contentTitle: String, contentText: String, progressPercent: Int) {
        downloadProgressNotification.apply {
            setContentTitle(contentTitle)
            setContentText(contentText)
            setProgress(100, progressPercent, false)
            notificationManagerCompat.notifyCompat(applicationContext, BULK_DOWNLOAD_ID, build())
        }
    }

    private suspend fun startOfflineDownload(file: File, offlineFile: IOFile): Result = withContext(Dispatchers.IO) {
        val okHttpClient = AccountUtils.getHttpClient(userDrive.userId, null)
        val response = downloadWorkerUtils.downloadFileResponse(
            fileUrl = ApiRoutes.downloadFile(file),
            okHttpClient = okHttpClient,
            downloadInterceptor = downloadWorkerUtils.downloadProgressInterceptor(
                getMostRecentLastUpdate = { lastUpdateProgressMillis },
                onLastUpdateChange = { lastUpdate -> lastUpdateProgressMillis = lastUpdate },
                onProgress = { progress ->
                    ensureActive()

                    setProgressAsync(workDataOf(PROGRESS to progress, FILE_ID to file.id))
                    SentryLog.d(TAG, "download $progress%")
                }
            )
        )

        downloadWorkerUtils.saveRemoteData(response, offlineFile) {
            setProgressAsync(workDataOf(PROGRESS to 100, FILE_ID to file.id))
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
        numberOfFilesDownloaded += 1

        val progressPercent = (numberOfFilesDownloaded * 100) / filesCount
        val notificationContentTitle = applicationContext.getString(
            R.string.bulkDownloadNotificationTitleWithProgress,
            progressPercent
        )
        val notificationContentText = applicationContext.resources.getQuantityString(
            R.plurals.bulkDownloadNotificationContent,
            numberOfFilesDownloaded,
            numberOfFilesDownloaded,
            filesCount
        )
        updateDownloadNotification(
            contentTitle = notificationContentTitle,
            contentText = notificationContentText,
            progressPercent = progressPercent
        )
    }

    private fun notifyDownloadCancelled() {
        Intent().apply {
            action = TAG
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(this)
        }
    }

    companion object {
        const val TAG = "BulkDownloadWorker"
        const val DRIVE_ID = "drive_id"
        const val FILE_IDS = "file_ids"
        const val FILE_ID = "file_id"
        const val PROGRESS = "progress"
        const val USER_ID = "user_id"
    }
}
