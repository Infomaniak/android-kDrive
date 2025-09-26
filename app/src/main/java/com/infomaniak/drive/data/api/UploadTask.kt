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
@file:OptIn(ExperimentalAtomicApi::class, ExperimentalSplittiesApi::class)

package com.infomaniak.drive.data.api

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.work.Data
import androidx.work.workDataOf
import com.google.gson.annotations.SerializedName
import com.infomaniak.core.io.skipExactly
import com.infomaniak.core.ktor.toOutgoingContent
import com.infomaniak.core.rateLimit
import com.infomaniak.core.sentry.SentryLog
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
import com.infomaniak.lib.core.networking.ManualAuthorizationRequired
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.retry
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.invoke
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import splitties.coroutines.raceOf
import splitties.experimental.ExperimentalSplittiesApi
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.minusAssign
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KSuspendFunction1
import kotlin.time.Duration.Companion.seconds

class UploadTask(
    private val context: Context,
    private val uploadFile: UploadFile,
    private val setProgress: KSuspendFunction1<Data, Unit>,
) {

    private val fileChunkSizeManager = FileChunkSizeManager()

    private var currentProgress = 0

    private val uploadedBytes = AtomicLong(value = 0L)
    private val uploadedBytesUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, replay = 1).also {
        it.tryEmit(Unit)
    }

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
        } catch(exception: CancellationException) {
            throw exception
        } catch (exception: ValidationRuleMaxException) {
            Sentry.captureException(exception) { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("fileModifiedAt", "${uploadFile.fileModifiedAt}")
                scope.setExtra("fileCreatedAt", "${uploadFile.fileCreatedAt}")
            }

            uploadFile.deleteIfExists()
        } catch (exception: FileNotFoundException) {
            uploadFile.deleteIfExists(keepFile = uploadFile.isSync())
            SentryLog.w(TAG, "file not found", exception)
            Sentry.captureException(exception) { scope -> scope.level = SentryLevel.WARNING }
        } catch (exception: UploadNotTerminated) {
            SentryLog.w(TAG, "upload not terminated", exception)
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            Sentry.captureException(exception) { scope -> scope.level = SentryLevel.WARNING }
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

        SentryLog.d("kDrive", " upload task started with total chunks: $totalChunks, valid: $uploadedChunks")

        uploadedBytes.store(uploadedChunks?.uploadedSize ?: 0L)

        raceOf(
            {
                uploadChunks(
                    chunkConfig = chunkConfig,
                    validChunksIds = uploadedChunks?.validChunksIds ?: emptyList(),
                    isNewUploadSession = isNewUploadSession,
                    getInputStream = {
                        val uri = uploadFile.getOriginalUri(context)
                        context.contentResolver.openInputStream(uri)
                            ?: throw IOException("The provider for the following Uri recently crashed: $uri")
                    },
                    uploadHost = uploadHost,
                )
            },
            {
                uploadedBytesUpdates.rateLimit(minInterval = 1.seconds / 5).collect { _ ->
                    updateProgress()
                }
                awaitCancellation()
            },
        )

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
        validChunksIds: List<Int>,
        isNewUploadSession: Boolean,
        getInputStream: () -> InputStream,
        uploadHost: String,
    ) = coroutineScope {
        val requestSemaphore = Semaphore(chunkConfig.parallelChunks)
        val httpClient = HttpClient(OkHttp) {
            engine { preconfigured = uploadFile.okHttpClient }
        }

        for (chunkNumber in 1..chunkConfig.totalChunks) launch {
            requestSemaphore.withPermit {
                if (chunkNumber in validChunksIds && !isNewUploadSession) {
                    SentryLog.d("kDrive", "chunk:$chunkNumber ignored")
                    return@launch
                }
                SentryLog.i("kDrive", "Upload > File chunks number: $chunkNumber has permission")
                uploadChunk(
                    getInputStream = getInputStream,
                    httpClient = httpClient,
                    uploadHost = uploadHost,
                    chunkConfig = chunkConfig,
                    chunkNumber = chunkNumber,
                )
            }
        }
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

    private suspend fun uploadChunk(
        getInputStream: () -> InputStream,
        httpClient: HttpClient,
        uploadHost: String,
        chunkConfig: FileChunkSizeManager.ChunkConfig,
        chunkNumber: Int,
    ): Unit = coroutineScope {
        val chunkIndex = chunkNumber - 1
        val chunkSize = chunkConfig.fileChunkSize
        val totalChunks = chunkConfig.totalChunks
        val isLastChunk = chunkNumber == totalChunks
        val currentChunkSize = when {
            isLastChunk -> (uploadFile.fileSize % chunkSize).let { if (it == 0L) chunkSize else it }
            else -> chunkSize
        }
        val url = uploadChunkUrl(
            driveId = uploadFile.driveId,
            uploadToken = uploadFile.uploadToken,
            chunkNumber = chunkNumber,
            currentChunkSize = currentChunkSize,
            uploadHost = uploadHost,
        )
        SentryLog.d("kDrive", "Upload > Start upload ${uploadFile.fileName} to $url data size:$currentChunkSize")
        getInputStream().buffered().use { stream ->
            Dispatchers.IO {
                stream.skipExactly(
                    numberOfBytes = chunkSize * chunkIndex,
                    coroutineContext = coroutineContext,
                )
            }
            uploadChunkUnchecked(
                preSkippedStream = stream,
                httpClient = httpClient,
                url = url,
                length = currentChunkSize,
            )
        }
    }

    private suspend fun uploadChunkUnchecked(
        preSkippedStream: InputStream,
        httpClient: HttpClient,
        url: String,
        length: Long,
    ) {
        var oldBytesSentTotal = 0L
        try {
            // We are using `preparePost` + `execute` instead of just `post` because `post` tries to save the call,
            // and it is triggering an internal error because of a mismatch between the response contentLength,
            // and the size of the ByteArray of the body.
            // With `preparePost`, `execute`, and `bodyAsChannel()`, we are not getting the issue,
            // and the size seem to match, so it might be a ktor or OkHttp internal issue worth reporting.
            httpClient.preparePost(url) {
                headers {
                    @OptIn(ManualAuthorizationRequired::class)
                    HttpUtils.getHeaders(contentType = null).forEach { (name, value) ->
                        append(name, value)
                    }
                }
                retry { noRetry() }
                setBody(preSkippedStream.toOutgoingContent(length = length))
                onUpload { bytesSentTotal, contentLength ->
                    val bytesJustSent = bytesSentTotal - oldBytesSentTotal
                    oldBytesSentTotal = bytesSentTotal
                    val currentTotalBytes = uploadedBytes.addAndFetch(bytesJustSent)
                    if (currentTotalBytes > uploadFile.fileSize) {
                        uploadFile.resetUploadTokenAndCancelSession()
                        SentryLog.d(
                            "UploadWorker",
                            "progress >> expected file size exceeded: $currentTotalBytes/${uploadFile.fileSize}",
                        )
                        throw WrittenBytesExceededException()
                    }
                    uploadedBytesUpdates.tryEmit(Unit)
                }
            }.execute { response ->
                manageApiResponse(response)
            }
        } catch (t: Throwable) {
            uploadedBytes -= oldBytesSentTotal
            uploadedBytesUpdates.tryEmit(Unit)
            throw t
        }
    }

    private fun ValidChunks.needToResetUpload(chunkSize: Long): Boolean {
        return if (expectedSize != uploadFile.fileSize || validChuckSize != chunkSize.toInt()) {
            uploadFile.resetUploadTokenAndCancelSession()
            true
        } else {
            uploadFile.uploadHost == null
        }
    }

    private suspend fun manageApiResponse(response: HttpResponse) {
        val isSuccessful = response.status.isSuccess()
        SentryLog.i("UploadTask", "response successful $isSuccessful")
        if (!isSuccessful) {
            val bytes = response.bodyAsChannel().toByteArray().also { bytes ->
                val expectedContentLength = response.contentLength() ?: bytes.size
                if (expectedContentLength != bytes.size) {
                    Sentry.captureMessage(
                        "Backend provided more or fewer bytes than the contentLength it declared!",
                        SentryLevel.WARNING,
                    ) { scope ->
                        scope.setExtra("contentLength", expectedContentLength.toString())
                        scope.setExtra("received", bytes.size.toString())
                    }
                }
            }
            val bodyResponse = String(bytes)
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            val apiResponse = try {
                gson.fromJson(bodyResponse, ApiResponse::class.java)!! // Might be empty when http 502 Bad gateway happens
            } catch (_: Exception) {
                ApiResponse<Any>(error = ApiError(description = bodyResponse))
            }
            apiResponse.manageUploadErrors()
        }
    }

    private suspend fun updateProgress() {
        val totalBytesWritten = uploadedBytes.load()
        val progress = ((totalBytesWritten.toDouble() / uploadFile.fileSize.toDouble()) * 100).toInt()
        currentProgress = progress

        SentryLog.i(
            "kDrive",
            " upload >> ${progress}%, totalBytesWritten:$totalBytesWritten, fileSize:${uploadFile.fileSize}",
        )

        currentCoroutineContext().ensureActive()

        if (uploadNotificationElapsedTime >= ELAPSED_TIME) {
            uploadNotification.apply {
                setContentIntent(uploadFile.progressPendingIntent(context))
                setContentText("${progress}%")
                setProgress(100, progress, false)
                notificationManagerCompat.notifyCompat(context, CURRENT_UPLOAD_ID, build())
                uploadNotificationStartTime = System.currentTimeMillis()
                uploadNotificationElapsedTime = 0L
            }
        } else {
            uploadNotificationElapsedTime = System.currentTimeMillis() - uploadNotificationStartTime
        }

        if (progress in 1..100) shareProgress(progress)
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
            createdAt = if (fileCreatedAt == null) null else fileCreatedAt!!.time / 1_000L,
            directoryId = remoteFolder,
            fileName = fileName,
            lastModifiedAt = fileModifiedAt.time / 1_000L,
            subDirectoryPath = remoteSubFolder ?: "",
            totalChunks = totalChunks,
            totalSize = fileSize,
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
            "validation_rule_max" -> throw ValidationRuleMaxException()
            else -> {
                if (error?.exception is ApiController.ServerErrorException) {
                    uploadFile.resetUploadTokenAndCancelSession()
                    throw UploadErrorException()
                } else {
                    val responseJson = gson.toJson(this)
                    throw Exception("$responseJson translateError: ${context.getString(translateError())}")
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

    fun previousChunkBytesWritten() = uploadedBytes.load()

    fun lastProgress() = currentProgress

    class FolderNotFoundException : Exception()
    class LockErrorException : Exception()
    class NetworkException : Exception()
    class NotAuthorizedException : Exception()
    class ProductBlockedException : Exception()
    class ValidationRuleMaxException : Exception()
    class ProductMaintenanceException : Exception()
    class QuotaExceededException : Exception()
    class UploadErrorException : Exception()
    class UploadNotTerminated(message: String) : Exception(message)
    class WrittenBytesExceededException : Exception()
    class LimitExceededException : Exception()

    companion object {
        private val TAG = UploadTask::class.java.simpleName

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
