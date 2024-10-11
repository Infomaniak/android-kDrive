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
package com.infomaniak.drive.data.api

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.workDataOf
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.data.api.ApiRepository.uploadEmptyFile
import com.infomaniak.drive.data.api.ApiRoutes.uploadChunkUrl
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Drive.MaintenanceReason
import com.infomaniak.drive.data.models.upload.UploadSession
import com.infomaniak.drive.data.models.upload.ValidChunks
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.UploadNotifications.progressPendingIntent
import com.infomaniak.drive.utils.NotificationUtils.CURRENT_UPLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.ELAPSED_TIME
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import com.infomaniak.drive.utils.NotificationUtils.uploadProgressNotification
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.api.ApiController.gson
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.util.Date
import kotlin.math.ceil

class UploadTask(
    private val context: Context,
    private val uploadFile: UploadFile,
    private val worker: UploadWorker,
    private val onProgress: ((progress: Int) -> Unit)? = null
) {

    private var limitParallelRequest = 4
    private var previousChunkBytesWritten = 0L
    private var currentProgress = 0

    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private lateinit var uploadNotification: NotificationCompat.Builder
    private var uploadNotificationElapsedTime = ELAPSED_TIME
    private var uploadNotificationStartTime = 0L

    suspend fun start() = withContext(Dispatchers.IO) {
        notificationManagerCompat = NotificationManagerCompat.from(context)

        uploadNotification = context.uploadProgressNotification()
        uploadNotification.apply {
            setContentTitle(uploadFile.fileName)
            notificationManagerCompat.notifyCompat(context, CURRENT_UPLOAD_ID, build())
        }

        try {
            if (uploadFile.fileSize == 0L) uploadEmptyFile(uploadFile) else launchTask(this)
            return@withContext true
        } catch (exception: FileNotFoundException) {
            uploadFile.deleteIfExists(keepFile = uploadFile.isSync())
            SentryLog.w(TAG, "file not found", exception)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                Sentry.captureException(exception)
            }
        } catch (exception: TotalChunksExceededException) {
            SentryLog.w(TAG, "total chunks exceeded", exception)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("half heap", "${getAvailableHalfMemory()}")
                scope.setExtra("available ram memory", "${context.getAvailableMemory().availMem}")
                scope.setExtra("available service memory", "${context.getAvailableMemory().threshold}")
                Sentry.captureException(exception)
            }
        } catch (exception: UploadNotTerminated) {
            SentryLog.w(TAG, "upload not terminated", exception)
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                Sentry.captureException(exception)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            throw exception
        } finally {
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
        }
        return@withContext false
    }

    private suspend fun launchTask(coroutineScope: CoroutineScope) = withContext(Dispatchers.IO) {
        val uri = uploadFile.getUriObject()
        context.contentResolver.openInputStream(uploadFile.getOriginalUri(context)).use { fileInputStream ->
            initChunkSize(uploadFile.fileSize)

            val uploadedChunks = uploadFile.getValidChunks()
            val isNewUploadSession = uploadedChunks?.needToResetUpload() ?: true
            val totalChunks = ceil(uploadFile.fileSize.toDouble() / chunkSize).toInt()

            val uploadHost = if (isNewUploadSession) {
                uploadFile.prepareUploadSession(totalChunks)
            } else {
                chunkSize = uploadedChunks!!.validChuckSize
                uploadFile.uploadHost
            }!!

            BufferedInputStream(fileInputStream, chunkSize * 2).use { input ->
                val chunkParentJob = Job()
                val requestSemaphore = Semaphore(limitParallelRequest)

                if (totalChunks > TOTAL_CHUNKS) throw TotalChunksExceededException()

                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = UploadWorker.BREADCRUMB_TAG
                    message = "start ${uploadFile.fileName} with $totalChunks chunks and $uploadedChunks uploadedChunks"
                    level = SentryLevel.INFO
                })

                SentryLog.d("kDrive", " upload task started with total chunk: $totalChunks, valid: $uploadedChunks")

                val validChunksIds = uploadedChunks?.validChunksIds
                previousChunkBytesWritten = uploadedChunks?.uploadedSize ?: 0

                for (chunkNumber in 1..totalChunks) {
                    requestSemaphore.acquire()
                    if (validChunksIds?.contains(chunkNumber) == true && !isNewUploadSession) {
                        SentryLog.d("kDrive", "chunk:$chunkNumber ignored")
                        input.skip(chunkSize.toLong())
                        requestSemaphore.release()
                        continue
                    }

                    SentryLog.i("kDrive", "Upload > File chunks number: $chunkNumber has permission")
                    var data = ByteArray(chunkSize)
                    val count = input.read(data, 0, chunkSize)
                    if (count == -1) {
                        requestSemaphore.release()
                        continue
                    }

                    data = if (count == chunkSize) data else data.copyOf(count)

                    val url = with(uploadFile) {
                        uploadChunkUrl(
                            driveId = driveId,
                            uploadToken = uploadToken,
                            chunkNumber = chunkNumber,
                            currentChunkSize = count,
                            uploadHost = uploadHost,
                        )
                    }

                    SentryLog.d("kDrive", "Upload > Start upload ${uploadFile.fileName} to $url data size:${data.size}")

                    @Suppress("DeferredResultUnused")
                    coroutineScope.async(chunkParentJob) {
                        uploadChunkRequest(requestSemaphore, data.toRequestBody(), url)
                    }
                }
                chunkParentJob.complete()
                chunkParentJob.join()
            }
        }

        coroutineScope.ensureActive()
        onFinish(uri)
    }

    private suspend fun onFinish(uri: Uri) = with(uploadFile) {
        with(ApiRepository.finishSession(driveId, uploadToken!!, okHttpClient)) {
            if (!isSuccess()) manageUploadErrors()
        }
        uploadNotification.apply {
            setOngoing(false)
            setContentText("100%")
            setSmallIcon(android.R.drawable.stat_sys_upload_done)
            setProgress(0, 0, false)
            notificationManagerCompat.notifyCompat(context, CURRENT_UPLOAD_ID, build())
        }
        shareProgress(100, true)
        UploadFile.uploadFinished(uri)
        notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
    }

    private fun uploadChunkRequest(
        requestSemaphore: Semaphore,
        requestBody: RequestBody,
        url: String,
    ) {
        val uploadRequestBody = ProgressRequestBody(requestBody) { currentBytes, _, _ ->
            runBlocking { progressMutex.withLock { updateProgress(currentBytes) } }
        }

        val request = Request.Builder().url(url)
            .headers(HttpUtils.getHeaders(contentType = null))
            .post(uploadRequestBody).build()

        val response = uploadFile.okHttpClient.newCall(request).execute()
        manageApiResponse(response)
        requestSemaphore.release()
    }

    private fun initChunkSize(fileSize: Long) {
        val fileChunkSize = ceil(fileSize.toDouble() / OPTIMAL_TOTAL_CHUNKS).toInt()

        when {
            fileChunkSize < CHUNK_MIN_SIZE -> chunkSize = CHUNK_MIN_SIZE
            fileChunkSize <= CHUNK_MAX_SIZE -> chunkSize = fileChunkSize
            fileChunkSize > CHUNK_MAX_SIZE -> {
                val totalChunks = ceil(fileSize.toDouble() / CHUNK_MAX_SIZE)
                if (totalChunks <= TOTAL_CHUNKS) {
                    chunkSize = CHUNK_MAX_SIZE
                } else {
                    Sentry.withScope { scope ->
                        scope.level = SentryLevel.WARNING
                        scope.setExtra("fileSize", "$fileSize")
                        Sentry.captureMessage("Max chunk size exceeded, file size exceed $MAX_TOTAL_CHUNKS_SIZE")
                    }
                    throw AllowedFileSizeExceededException()
                }
            }
        }

        val availableHalfMemory = getAvailableHalfMemory()

        if (chunkSize >= availableHalfMemory) {
            chunkSize = availableHalfMemory.toInt()
        }

        if (chunkSize == 0) throw OutOfMemoryError("chunk size is 0")

        if (chunkSize * limitParallelRequest >= availableHalfMemory) {
            limitParallelRequest = 1 // We limit it to 1 because if we have more it throws OOF exceptions
        }
    }

    private fun ValidChunks.needToResetUpload(): Boolean {
        return if (expectedSize != uploadFile.fileSize || validChuckSize != chunkSize) {
            uploadFile.resetUploadTokenAndCancelSession()
            true
        } else uploadFile.uploadHost == null
    }

    private fun manageApiResponse(response: Response) {
        response.use {
            val bodyResponse = it.body?.string()
            SentryLog.i("UploadTask", "response successful ${it.isSuccessful}")
            if (!it.isSuccessful) {
                notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
                val apiResponse = try {
                    gson.fromJson(bodyResponse, ApiResponse::class.java)
                } catch (e: Exception) {
                    ApiResponse<Any>(error = ApiError(description = bodyResponse))
                }
                apiResponse.manageUploadErrors()
            }
        }
    }

    private fun CoroutineScope.updateProgress(currentBytes: Int) {
        val totalBytesWritten = currentBytes + previousChunkBytesWritten
        val progress = ((totalBytesWritten.toDouble() / uploadFile.fileSize.toDouble()) * 100).toInt()
        currentProgress = progress
        previousChunkBytesWritten += currentBytes

        if (previousChunkBytesWritten > uploadFile.fileSize) {
            uploadFile.resetUploadTokenAndCancelSession()
            SentryLog.d(
                "UploadWorker",
                "progress >> file exceed with ${uploadFile.fileSize}/${previousChunkBytesWritten}"
            )
            throw WrittenBytesExceededException()
        }

        onProgress?.invoke(progress)

        if (worker.isStopped) throw CancellationException()
        ensureActive()

        if (uploadNotificationElapsedTime >= ELAPSED_TIME) {
            uploadNotification.apply {
                setContentIntent(uploadFile.progressPendingIntent(context))
                setContentText("${currentProgress}%")
                setProgress(100, currentProgress, false)
                notificationManagerCompat.notifyCompat(context, CURRENT_UPLOAD_ID, build())
                uploadNotificationStartTime = System.currentTimeMillis()
                uploadNotificationElapsedTime = 0L
            }
        } else uploadNotificationElapsedTime = Date().time - uploadNotificationStartTime

        if (progress in 1..100) {
            launch { shareProgress(currentProgress) }
        }

        SentryLog.i(
            "kDrive",
            " upload >> ${currentProgress}%, totalBytesWritten:$totalBytesWritten, fileSize:${uploadFile.fileSize}"
        )
    }

    private suspend fun shareProgress(progress: Int = 0, isUploaded: Boolean = false) {
        worker.setProgress(
            workDataOf(
                UploadWorker.FILENAME to uploadFile.fileName,
                UploadWorker.PROGRESS to progress,
                UploadWorker.IS_UPLOADED to isUploaded,
                UploadWorker.REMOTE_FOLDER_ID to uploadFile.remoteFolder
            )
        )
    }

    private fun UploadFile.getValidChunks(): ValidChunks? {
        return uploadToken?.let { ApiRepository.getValidChunks(uploadFile.driveId, it, okHttpClient).data }
    }

    private fun UploadFile.prepareUploadSession(totalChunks: Int): String? {
        val sessionBody = UploadSession.StartSessionBody(
            conflict = if (replaceOnConflict()) ConflictOption.VERSION else ConflictOption.RENAME,
            createdAt = if (fileCreatedAt == null) null else fileCreatedAt!!.time / 1000,
            directoryId = remoteFolder,
            fileName = fileName,
            lastModifiedAt = fileModifiedAt.time / 1000,
            subDirectoryPath = remoteSubFolder ?: "",
            totalChunks = totalChunks,
            totalSize = fileSize
        )

        return ApiRepository.startUploadSession(driveId, sessionBody, okHttpClient).also {
            if (it.isSuccess()) it.data?.token?.let { uploadToken ->
                uploadFile.updateUploadToken(uploadToken, it.data!!.uploadHost)
            } else {
                it.manageUploadErrors()
            }
        }.data?.uploadHost
    }

    private fun <T> ApiResponse<T>.manageUploadErrors() {
        if (error?.exception is ApiController.NetworkException) throw NetworkException()
        when (error?.code) {
            "file_already_exists_error" -> Unit
            "lock_error" -> throw LockErrorException()
            "not_authorized" -> throw NotAuthorizedException()
            "product_maintenance" -> {
                if (error?.contextGson?.getAsJsonPrimitive("reason")?.asString == MaintenanceReason.TECHNICAL.value) {
                    throw ProductMaintenanceException()
                } else {
                    throw ProductBlockedException()
                }
            }
            ErrorCode.QUOTA_EXCEEDED_ERROR -> throw QuotaExceededException()
            "upload_destination_not_found_error", "upload_destination_not_writable_error" -> throw FolderNotFoundException()
            "upload_not_terminated", "upload_not_terminated_error" -> {
                // Upload finish with 0 chunks uploaded
                // Upload finish with a different expected number of chunks
                uploadFile.resetUploadTokenAndCancelSession()
                throw UploadNotTerminated("Upload finish with 0 chunks uploaded or a different expected number of chunks")
            }
            "invalid_upload_token_error",
            "object_not_found",
            "upload_error",
            "upload_failed_error",
            "upload_token_is_not_valid" -> {
                uploadFile.resetUploadTokenAndCancelSession()
                throw UploadErrorException()
            }
            LIMIT_EXCEEDED_ERROR_CODE -> throw LimitExceededException()
            else -> {
                if (error?.exception is ApiController.ServerErrorException) {
                    uploadFile.resetUploadTokenAndCancelSession()
                    throw UploadErrorException()
                } else {
                    val responseType = object : TypeToken<ApiResponse<T>>() {}.type
                    val responseJson = gson.toJson(this, responseType)
                    val translatedError = if (translatedError == 0) "" else context.getString(translatedError)
                    throw Exception("$responseJson translatedError: $translatedError")
                }
            }
        }
    }

    private fun UploadFile.resetUploadTokenAndCancelSession() {
        uploadToken?.let {
            with(ApiRepository.cancelSession(driveId, it, okHttpClient)) {
                if (data != null) resetUploadToken()
            }
        }
    }

    /**
     * Returns half the available memory in the ram, to avoid [OutOfMemoryError]
     * @return half available memory
     */
    private fun getAvailableHalfMemory() = context.getAvailableMemory().availMem / 2

    fun previousChunkBytesWritten() = previousChunkBytesWritten

    fun lastProgress() = currentProgress

    class AllowedFileSizeExceededException : Exception()
    class FolderNotFoundException : Exception()
    class LockErrorException : Exception()
    class NetworkException : Exception()
    class NotAuthorizedException : Exception()
    class ProductBlockedException : Exception()
    class ProductMaintenanceException : Exception()
    class QuotaExceededException : Exception()
    class TotalChunksExceededException : Exception()
    class UploadErrorException : Exception()
    class UploadNotTerminated(message: String) : Exception(message)
    class WrittenBytesExceededException : Exception()
    class LimitExceededException : Exception()

    companion object {
        private val TAG = UploadTask::class.java.simpleName
        private val progressMutex = Mutex()

        private const val CHUNK_MIN_SIZE: Int = 1 * 1024 * 1024
        private const val CHUNK_MAX_SIZE: Int = 50 * 1024 * 1024 // 50 Mo and max file size to upload 500Gb
        private const val OPTIMAL_TOTAL_CHUNKS: Int = 200
        private const val TOTAL_CHUNKS: Int = 10_000
        private const val MAX_TOTAL_CHUNKS_SIZE: Long = CHUNK_MAX_SIZE.toLong() * TOTAL_CHUNKS.toLong()

        const val LIMIT_EXCEEDED_ERROR_CODE = "limit_exceeded_error"

        var chunkSize: Int = CHUNK_MIN_SIZE

        enum class ConflictOption {
            @SerializedName("error")
            ERROR,

            @SerializedName("version")
            VERSION,

            @SerializedName("rename")
            RENAME;

            override fun toString(): String = name.lowercase()
        }
    }
}
