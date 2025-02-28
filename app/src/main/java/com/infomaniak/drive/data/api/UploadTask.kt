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
import androidx.work.Data
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
import kotlinx.coroutines.channels.Channel
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
import java.util.concurrent.ArrayBlockingQueue
import kotlin.reflect.KSuspendFunction1

class UploadTask(
    private val context: Context,
    private val uploadFile: UploadFile,
    private val setProgress: KSuspendFunction1<Data, Unit>,
) {

    private val fileChunkSizeManager = FileChunkSizeManager()

    private var previousChunkBytesWritten = 0L
    private var currentProgress = 0

    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private lateinit var uploadNotification: NotificationCompat.Builder
    private var uploadNotificationElapsedTime = ELAPSED_TIME
    private var uploadNotificationStartTime = 0L

    suspend fun start(): Boolean {
        notificationManagerCompat = NotificationManagerCompat.from(context)

        uploadNotification = context.uploadProgressNotification()
        uploadNotification.apply {
            setContentTitle(uploadFile.fileName)
            notificationManagerCompat.notifyCompat(context, CURRENT_UPLOAD_ID, build())
        }

        try {
            if (uploadFile.fileSize == 0L) uploadEmptyFile(uploadFile) else launchTask()
            return true
        } catch (exception: FileNotFoundException) {
            uploadFile.deleteIfExists(keepFile = uploadFile.isSync())
            SentryLog.w(TAG, "file not found", exception)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
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
        return false
    }

    private suspend fun launchTask() = coroutineScope {
        val chunkConfig = getChunkConfig()
        val totalChunks = chunkConfig.totalChunks
        val uploadedChunks = uploadFile.getValidChunks()
        val isNewUploadSession = uploadedChunks?.needToResetUpload(chunkConfig.fileChunkSize) ?: true

        val uploadHost = if (isNewUploadSession) {
            uploadFile.prepareUploadSession(totalChunks)
        } else {
            uploadFile.uploadHost
        }!!

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = UploadWorker.BREADCRUMB_TAG
            message = "start ${uploadFile.uri} with $totalChunks chunks and $uploadedChunks uploadedChunks"
            level = SentryLevel.INFO
        })

        context.contentResolver.openInputStream(uploadFile.getOriginalUri(context))?.buffered()?.use { fileInputStream ->

            SentryLog.d("kDrive", " upload task started with total chunk: $totalChunks, valid: $uploadedChunks")

            previousChunkBytesWritten = uploadedChunks?.uploadedSize ?: 0

            uploadChunks(
                chunkConfig = chunkConfig,
                validChunksIds = uploadedChunks?.validChunksIds,
                isNewUploadSession = isNewUploadSession,
                inputStream = fileInputStream,
                uploadHost = uploadHost,
            )
        }

        if (isActive) onFinish(uploadFile.getUriObject())
    }

    private fun getChunkConfig(): FileChunkSizeManager.ChunkConfig {
        return try {
            fileChunkSizeManager.computeChunkConfig(
                fileSize = uploadFile.fileSize,
                defaultFileChunkSize = uploadFile.getValidChunks()?.validChuckSize?.toLong(),
            )
        } catch (exception: IllegalArgumentException) {
            uploadFile.resetUploadTokenAndCancelSession()
            fileChunkSizeManager.computeChunkConfig(fileSize = uploadFile.fileSize)
        }
    }

    private suspend fun uploadChunks(
        chunkConfig: FileChunkSizeManager.ChunkConfig,
        validChunksIds: List<Int>?,
        isNewUploadSession: Boolean,
        inputStream: BufferedInputStream,
        uploadHost: String,
    ) = coroutineScope {
        val requestSemaphore = Semaphore(chunkConfig.parallelChunks)
        val byteArrayPool = ArrayBlockingQueue<ByteArray>(chunkConfig.parallelChunks)

        for (chunkNumber in 1..chunkConfig.totalChunks) {
            requestSemaphore.acquire()
            if (validChunksIds?.contains(chunkNumber) == true && !isNewUploadSession) {
                SentryLog.d("kDrive", "chunk:$chunkNumber ignored")
                inputStream.skip(chunkConfig.fileChunkSize)
                requestSemaphore.release()
                continue
            }

            SentryLog.i("kDrive", "Upload > File chunks number: $chunkNumber has permission")

            val data = getReusableByteArray(
                byteArrayPool = byteArrayPool,
                chunkSize = chunkConfig.fileChunkSize,
                inputStream = inputStream,
                isLastChunk = chunkNumber == chunkConfig.totalChunks,
            )
            val count = inputStream.read(data, 0, data.size)

            if (count == -1) {
                requestSemaphore.release()
                continue
            }

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

            launch {
                uploadChunkRequest(requestSemaphore, data.toRequestBody(), url)
            }
        }
    }

    private fun getReusableByteArray(
        byteArrayPool: ArrayBlockingQueue<ByteArray>,
        chunkSize: Long,
        inputStream: BufferedInputStream,
        isLastChunk: Boolean,
    ): ByteArray {
        val currentChunkSize = if (isLastChunk) inputStream.available() else chunkSize.toInt()
        val reusableByteArray = if (isLastChunk) null else byteArrayPool.poll()

        return reusableByteArray ?: ByteArray(currentChunkSize)
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

    private suspend fun uploadChunkRequest(
        requestSemaphore: Semaphore,
        requestBody: RequestBody,
        url: String,
    ) = coroutineScope {
        val progressChannel = Channel<Int>(Channel.UNLIMITED)

        val progressJob = launch {
            for (currentBytes in progressChannel) {
                progressMutex.withLock { updateProgress(currentBytes) }
            }
        }

        val uploadRequestBody = ProgressRequestBody(requestBody) { currentBytes, _, _ ->
            progressChannel.trySend(currentBytes)
        }

        val request = Request.Builder().url(url)
            .headers(HttpUtils.getHeaders(contentType = null))
            .post(uploadRequestBody).build()

        try {
            val response = uploadFile.okHttpClient.newCall(request).execute()
            manageApiResponse(response)
        } finally {
            progressChannel.cancel()
            progressJob.join()
            requestSemaphore.release()
        }
    }

    private fun ValidChunks.needToResetUpload(chunkSize: Long): Boolean {
        return if (expectedSize != uploadFile.fileSize || validChuckSize != chunkSize.toInt()) {
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
        setProgress(
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
            ApiRepository.cancelSession(driveId, it, okHttpClient)
            resetUploadToken()
        }
    }

    fun previousChunkBytesWritten() = previousChunkBytesWritten

    fun lastProgress() = currentProgress

    class FolderNotFoundException : Exception()
    class LockErrorException : Exception()
    class NetworkException : Exception()
    class NotAuthorizedException : Exception()
    class ProductBlockedException : Exception()
    class ProductMaintenanceException : Exception()
    class QuotaExceededException : Exception()
    class UploadErrorException : Exception()
    class UploadNotTerminated(message: String) : Exception(message)
    class WrittenBytesExceededException : Exception()
    class LimitExceededException : Exception()

    companion object {
        private val TAG = UploadTask::class.java.simpleName
        private val progressMutex = Mutex()

        const val LIMIT_EXCEEDED_ERROR_CODE = "limit_exceeded_error"

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
