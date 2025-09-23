/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import android.app.job.JobParameters
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.RemoteFileException
import com.infomaniak.drive.utils.getAvailableStorageInBytes
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException

abstract class BaseDownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    abstract val notificationId: Int
    abstract val notificationTitle: String

    protected val notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)
    protected val downloadProgressNotification by lazy {
        DownloadOfflineFileManager.createDownloadNotification(
            context = context,
            id = id,
            title = notificationTitle
        )
    }

    override suspend fun doWork(): Result {
        return runCatching {
            SentryLog.i(workerTag(), "Work started")
            val memoryLeftAfterDownload = (getAvailableStorageInBytes() - getSizeOfDownload()) / BYTES_TO_MB
            val hasSpaceLeftAfterDownload = memoryLeftAfterDownload > MIN_SPACE_LEFT_AFTER_DOWNLOAD_MB
            if (hasSpaceLeftAfterDownload) {
                downloadAction()
            } else {
                SentryLog.i(
                    workerTag(),
                    "After the download of file(s), the device storage would have been below 500 MB so we cancel the download."
                )
                Result.failure(getNotEnoughSpaceOutputData())
            }
        }.getOrElse { throwable ->
            onWorkFailure(throwable)
        }.also {
            onFinish()
            Log.i(workerTag(), "Work finished")
        }
    }

    private fun getNotEnoughSpaceOutputData(): Data {
        return Data.Builder()
            .putBoolean(HAS_SPACE_LEFT_AFTER_DOWNLOAD_KEY, false)
            .build()
    }

    private fun onWorkFailure(throwable: Throwable): Result {
        throwable.printStackTrace()
        return when (throwable) {
            is CancellationException -> {
                if (SDK_INT >= 31 && stopReason == JobParameters.STOP_REASON_TIMEOUT) {
                    SentryLog.e(workerTag(), "Stopped because a time out error", throwable)
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
            is UploadTask.NetworkException -> {
                Result.retry()
            }
            is RemoteFileException -> {
                Sentry.captureException(throwable)
                Result.failure()
            }
            else -> {
                SentryLog.e(workerTag(), "Failure", throwable)
                Result.failure()
            }
        }
    }

    abstract fun downloadNotification(): DownloadNotification?

    abstract suspend fun downloadAction(): Result

    // Flush data when success or failure
    abstract fun onFinish()

    abstract fun isForOneFile(): Boolean

    abstract fun getSizeOfDownload(): Long

    abstract fun workerTag(): String

    override suspend fun getForegroundInfo() = ForegroundInfo(notificationId, downloadProgressNotification.build())

    data class DownloadNotification(
        val titleResId: Int? = null,
        val contentResId: Int? = null,
        val id: Int,
        val notification: NotificationCompat.Builder
    )

    companion object {
        const val HAS_SPACE_LEFT_AFTER_DOWNLOAD_KEY = "HAS_SPACE_LEFT_AFTER_DOWNLOAD_KEY"

        const val DRIVE_ID = "drive_id"
        const val FILE_ID = "file_id"
        const val PROGRESS = "progress"
        const val USER_ID = "user_id"

        private const val MIN_SPACE_LEFT_AFTER_DOWNLOAD_MB = 500
        private const val BYTES_TO_MB = 1_000_000
    }
}
