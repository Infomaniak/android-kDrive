/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import androidx.work.ListenableWorker.Result
import com.infomaniak.drive.data.api.FileChunkSizeManager.AllowedFileSizeExceededException
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.sync.UploadNotifications.allowedFileSizeExceededNotification
import com.infomaniak.drive.data.sync.UploadNotifications.exceptionNotification
import com.infomaniak.drive.data.sync.UploadNotifications.folderNotFoundNotification
import com.infomaniak.drive.data.sync.UploadNotifications.lockErrorNotification
import com.infomaniak.drive.data.sync.UploadNotifications.networkErrorNotification
import com.infomaniak.drive.data.sync.UploadNotifications.outOfMemoryNotification
import com.infomaniak.drive.data.sync.UploadNotifications.productMaintenanceExceptionNotification
import com.infomaniak.drive.data.sync.UploadNotifications.quotaExceededNotification
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.lib.core.utils.isNetworkException
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import java.io.IOException

object UploadWorkerThrowable {

    suspend fun UploadWorker.runUploadCatching(block: suspend () -> Result): Result {
        return try {
            block()

        } catch (exception: UploadTask.FolderNotFoundException) {
            currentUploadFile?.folderNotFoundNotification(applicationContext)
            Result.failure()

        } catch (exception: UploadTask.QuotaExceededException) {
            currentUploadFile?.quotaExceededNotification(applicationContext)
            Result.failure()

        } catch (exception: AllowedFileSizeExceededException) {
            currentUploadFile?.allowedFileSizeExceededNotification(applicationContext)
            Result.failure()

        } catch (exception: OutOfMemoryError) {
            exception.printStackTrace()
            currentUploadFile?.outOfMemoryNotification(applicationContext)
            Result.retry()

        } catch (exception: CancellationException) { // Work has been cancelled
            Result.retry()

        } catch (exception: UploadTask.LockErrorException) {
            currentUploadFile?.lockErrorNotification(applicationContext)
            Result.retry()

        } catch (exception: UploadTask.ProductBlockedException) {
            currentUploadFile?.productMaintenanceExceptionNotification(applicationContext, false)
            Result.failure()

        } catch (exception: UploadTask.ProductMaintenanceException) {
            currentUploadFile?.productMaintenanceExceptionNotification(applicationContext, true)
            Result.failure()

        } catch (exception: Exception) {
            exception.printStackTrace()
            handleGenericException(exception)

        } finally {
            cancelUploadNotification()
        }
    }

    private fun UploadWorker.handleGenericException(exception: Exception): Result {
        return when {

            exception is UploadTask.WrittenBytesExceededException ||
                    exception is UploadTask.NotAuthorizedException ||
                    exception is UploadTask.UploadErrorException -> Result.retry()

            exception.isNetworkException() || exception is UploadTask.NetworkException -> {
                currentUploadFile?.networkErrorNotification(applicationContext)
                Result.retry()
            }

            exception is UploadTask.LimitExceededException -> {
                currentUploadFile?.exceptionNotification(applicationContext, isLimitExceeded = true)
                Result.failure()
            }

            else -> {
                exception.printStackTrace()
                currentUploadFile?.exceptionNotification(applicationContext)
                Sentry.captureException(exception) { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("previousChunkBytesWritten", "${currentUploadTask?.previousChunkBytesWritten()}")
                    scope.setExtra("lastProgress", "${currentUploadTask?.lastProgress()}")
                }
                if (exception is IOException) Result.retry() else Result.failure()
            }
        }
    }

    private fun UploadWorker.cancelUploadNotification() {
        applicationContext.cancelNotification(NotificationUtils.UPLOAD_SERVICE_ID)
        applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = UploadWorker.BREADCRUMB_TAG
            message = "finish with $uploadedCount files uploaded"
            level = SentryLevel.INFO
        })
    }
}
