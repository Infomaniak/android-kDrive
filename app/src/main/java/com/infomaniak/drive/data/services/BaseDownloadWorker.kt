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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.utils.RemoteFileException
import com.infomaniak.lib.core.utils.SentryLog
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException

abstract class BaseDownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            SentryLog.i(workerTag(), "Work started")
            downloadAction()
        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stopReason == JobParameters.STOP_REASON_TIMEOUT) {
                        SentryLog.e(workerTag(), "Stopped because a time out error", exception)
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                is UploadTask.NetworkException -> {
                    Result.failure()
                }
                is RemoteFileException -> {
                    Sentry.captureException(exception)
                    Result.failure()
                }
                else -> {
                    SentryLog.e(workerTag(), "Failure", exception)
                    Result.failure()
                }
            }
        }.also {
            isFinished()
            Log.i(workerTag(), "Work finished")
        }
    }

    abstract fun downloadNotification(): DownloadNotification?

    abstract suspend fun downloadAction(): Result

    abstract fun isCanceled()

    abstract fun isFinished()

    abstract fun isForOneFile(): Boolean

    abstract fun workerTag(): String

    data class DownloadNotification(
        val titleResId: Int? = null,
        val contentResId: Int? = null,
        val id: Int,
        val notification: NotificationCompat.Builder
    )
}