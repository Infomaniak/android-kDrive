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
package com.infomaniak.drive.data.api

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.workDataOf
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.upload.UploadSession
import com.infomaniak.drive.data.models.upload.ValidChunks
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.NotificationUtils.CURRENT_UPLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.ELAPSED_TIME
import com.infomaniak.drive.utils.NotificationUtils.uploadProgressNotification
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.ApiController.gson
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.util.*
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
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }

        try {
            launchTask(this)
            return@withContext true
        } catch (exception: FileNotFoundException) {
            UploadFile.deleteIfExists(uploadFile.getUriObject(), keepFile = uploadFile.isSync())
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("data", gson.toJson(uploadFile))
                Sentry.captureException(exception)
            }

        } catch (exception: TotalChunksExceededException) {
            exception.printStackTrace()
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("half heap", "${getAvailableHalfHeapMemory()}")
                scope.setExtra("available ram memory", "${context.getAvailableMemory().availMem}")
                scope.setExtra("available service memory", "${context.getAvailableMemory().threshold}")
                Sentry.captureException(exception)
            }
        } catch (exception: UploadNotTerminated) {
            exception.printStackTrace()
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("data", gson.toJson(uploadFile))
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

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun launchTask(coroutineScope: CoroutineScope) = withContext(Dispatchers.IO) {
        val uri = uploadFile.getUriObject()
        val fileInputStream = context.contentResolver.openInputStream(uploadFile.getOriginalUri(context))

        initChunkSize(uploadFile.fileSize)
        checkLimitParallelRequest()

        BufferedInputStream(fileInputStream, chunkSize).use { input ->
            val waitingCoroutines = arrayListOf<Job>()
            val requestSemaphore = Semaphore(limitParallelRequest)
            val totalChunks = ceil(uploadFile.fileSize.toDouble() / chunkSize).toInt()

            if (totalChunks > TOTAL_CHUNKS) throw TotalChunksExceededException()

            val uploadedChunks = uploadFile.getValidChunks()
            val isNewUploadSession = uploadedChunks?.let { needToResetUpload(it) } ?: true

            if (isNewUploadSession) uploadFile.prepareUploadSession(totalChunks)

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = UploadWorker.BREADCRUMB_TAG
                message = "start ${uploadFile.fileName} with $totalChunks chunks and $uploadedChunks uploadedChunks"
                level = SentryLevel.INFO
            })

            Log.d("kDrive", " upload task started with total chunk: $totalChunks, valid: $uploadedChunks")

            val validChunksIds = uploadedChunks?.validChunksIds
            previousChunkBytesWritten = uploadedChunks?.uploadedSize ?: 0

            for (chunkNumber in 1..totalChunks) {
                requestSemaphore.acquire()
                if (validChunksIds?.contains(chunkNumber) == true && !isNewUploadSession) {
                    Log.d("kDrive", "chunk:$chunkNumber ignored")
                    input.skip(chunkSize.toLong())
                    requestSemaphore.release()
                    continue
                }

                Log.i("kDrive", "Upload > ${uploadFile.fileName} chunk:$chunkNumber has permission")
                var data = ByteArray(chunkSize)
                val count = input.read(data, 0, chunkSize)
                if (count == -1) {
                    requestSemaphore.release()
                    continue
                }

                data = if (count == chunkSize) data else data.copyOf(count)

                val url = uploadFile.uploadUrl(chunkNumber = chunkNumber, currentChunkSize = count)
                Log.d("kDrive", "Upload > Start upload ${uploadFile.fileName} to $url data size:${data.size}")

                waitingCoroutines.add(coroutineScope.uploadChunkRequest(requestSemaphore, data, url))
            }
            waitingCoroutines.joinAll()
        }

        coroutineScope.ensureActive()
        onFinish(uri)
    }

    private suspend fun onFinish(uri: Uri) {
        uploadNotification.apply {
            setOngoing(false)
            setContentText("100%")
            setSmallIcon(android.R.drawable.stat_sys_upload_done)
            setProgress(0, 0, false)
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }
        shareProgress(100, true)
        with(ApiRepository.finishSession(uploadFile.driveId, uploadFile.uploadToken!!)) {
            if (!isSuccess()) manageUploadErrors()
        }
        UploadFile.uploadFinished(uri)
        notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
    }

    private fun CoroutineScope.uploadChunkRequest(
        requestSemaphore: Semaphore,
        data: ByteArray,
        url: String
    ) = launch(Dispatchers.IO) {
        val uploadRequestBody = ProgressRequestBody(data.toRequestBody()) { currentBytes, bytesWritten, contentLength ->
            launch {
                progressMutex.withLock {
                    updateProgress(currentBytes, bytesWritten, contentLength)
                }
            }
        }

        val request = Request.Builder().url(url)
            .headers(HttpUtils.getHeaders(contentType = null))
            .post(uploadRequestBody).build()

        val response = KDriveHttpClient.getHttpClient(uploadFile.userId, 120).newCall(request).execute()
        manageApiResponse(response)
        requestSemaphore.release()
    }

    private fun initChunkSize(fileSize: Long) {
        val fileChunkSize = ceil(fileSize.toDouble() / OPTIMAL_TOTAL_CHUNKS).toInt()

        when {
            fileChunkSize in chunkSize..CHUNK_MAX_SIZE -> {
                chunkSize = fileChunkSize
            }
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

        val availableHeapMemory = getAvailableHalfHeapMemory()
        if (chunkSize >= availableHeapMemory) {
            chunkSize = ceil(availableHeapMemory.toDouble() / limitParallelRequest).toInt()
        }

        if (chunkSize == 0) throw OutOfMemoryError("chunk size is 0")
    }

    private fun needToResetUpload(uploadedChunks: ValidChunks): Boolean = with(uploadedChunks) {
        val uploadedChunksCount = chunks.count()
        val previousChunkSize = if (uploadedChunksCount == 0) 0 else uploadedSize.toInt() / uploadedChunksCount

        if (expectedSize != uploadFile.fileSize || previousChunkSize != chunkSize) {
            uploadFile.resetUploadToken()
            return true
        }

        return false
    }

    private fun manageApiResponse(response: Response) {
        response.use {
            val bodyResponse = it.body?.string()
            Log.i("UploadTask", "response successful ${it.isSuccessful}")
            if (!it.isSuccessful) {
                notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
                val apiResponse = try {
                    gson.fromJson(bodyResponse, ApiResponse::class.java)
                } catch (e: Exception) {
                    null
                }
                apiResponse.manageUploadErrors()
            }
        }
    }

    private fun CoroutineScope.updateProgress(currentBytes: Int, bytesWritten: Long, contentLength: Long) {
        val totalBytesWritten = currentBytes + previousChunkBytesWritten
        val progress = ((totalBytesWritten.toDouble() / uploadFile.fileSize.toDouble()) * 100).toInt()
        currentProgress = progress
        previousChunkBytesWritten += currentBytes

        if (previousChunkBytesWritten > uploadFile.fileSize) {
            uploadFile.resetUploadToken()
            Sentry.withScope { scope ->
                scope.setExtra("data", gson.toJson(uploadFile))
                scope.setExtra("file size", "${uploadFile.fileSize}")
                scope.setExtra("uploaded size", "$previousChunkBytesWritten")
                scope.setExtra("bytesWritten", "$bytesWritten")
                scope.setExtra("contentLength", "$contentLength")
                scope.setExtra("chunk size", "$chunkSize")
                Sentry.captureMessage("Chunk total size exceed fileSize 😢")
            }
            Log.d(
                "UploadWorker",
                "progress >> ${uploadFile.fileName} exceed with ${uploadFile.fileSize}/${previousChunkBytesWritten}"
            )
            throw WrittenBytesExceededException()
        }

        onProgress?.invoke(progress)

        if (worker.isStopped) throw CancellationException()
        ensureActive()

        if (uploadNotificationElapsedTime >= ELAPSED_TIME) {
            uploadNotification.apply {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.INTENT_SHOW_PROGRESS, uploadFile.remoteFolder)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0,
                    intent, UploadNotifications.pendingIntentFlags
                )
                setContentIntent(pendingIntent)
                setContentText("${currentProgress}%")
                setProgress(100, currentProgress, false)
                notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
                uploadNotificationStartTime = System.currentTimeMillis()
                uploadNotificationElapsedTime = 0L
            }
        } else uploadNotificationElapsedTime = Date().time - uploadNotificationStartTime

        if (progress in 1..100) {
            launch { shareProgress(currentProgress) }
        }

        Log.i(
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

    private fun checkLimitParallelRequest() = getAvailableHalfHeapMemory().let { availableHalfMemory ->
        if (chunkSize * limitParallelRequest >= availableHalfMemory) {
            limitParallelRequest = 1 // We limit it to 1 because if we have more it throws OOF exceptions
        }
    }

    private fun UploadFile.getValidChunks(): ValidChunks? {
        return uploadToken?.let { ApiRepository.getValidChunks(uploadFile.driveId, it).data }
    }

    private fun UploadFile.prepareUploadSession(totalChunks: Int) {
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

        with(ApiRepository.startUploadSession(driveId, sessionBody)) {
            if (isSuccess()) data?.token?.let { uploadFile.updateUploadToken(it) }
            else manageUploadErrors()
        }
    }

    private fun <T> ApiResponse<T>?.manageUploadErrors() {
        if (this?.translatedError == R.string.connectionError) throw NetworkException()
        when (this?.error?.code) {
            "file_already_exists_error" -> Unit
            "lock_error" -> throw LockErrorException()
            "object_not_found" -> throw FolderNotFoundException()
            "quota_exceeded_error" -> throw QuotaExceededException()
            "not_authorized" -> throw NotAuthorizedException()
            "upload_not_terminated" -> {
                // Upload finish with 0 chunks uploaded
                // Upload finish with a different expected number of chunks
                uploadFile.uploadToken?.let {
                    with(ApiRepository.cancelSession(uploadFile.driveId, it)) {
                        if (!isSuccess()) manageUploadErrors()
                    }
                }
                uploadFile.resetUploadToken()
                throw UploadNotTerminated("Upload finish with 0 chunks uploaded or a different expected number of chunks")
            }
            "upload_error" -> {
                uploadFile.resetUploadToken()
                throw UploadErrorException()
            }
            else -> {
                val responseType = object : TypeToken<ApiResponse<T>>() {}.type
                val responseJson = gson.toJson(this, responseType)
                throw this?.error?.description?.let(::Exception) ?: Exception(responseJson)
            }
        }
    }

    private fun UploadFile.uploadUrl(chunkNumber: Int, currentChunkSize: Int): String {
        return ApiRoutes.addChunkToSession(driveId, uploadToken!!) + "?chunk_number=$chunkNumber&chunk_size=$currentChunkSize"
    }

    /**
     * Returns half the available memory in the heap, to avoid [OutOfMemoryError]
     * It can vary depending on the available ram memory in the device
     * @return half available memory
     */
    private fun getAvailableHalfHeapMemory() = Runtime.getRuntime().freeMemory() / 2

    fun previousChunkBytesWritten() = previousChunkBytesWritten

    fun lastProgress() = currentProgress

    class AllowedFileSizeExceededException : Exception()
    class FolderNotFoundException : Exception()
    class LockErrorException : Exception()
    class NetworkException : Exception()
    class NotAuthorizedException : Exception()
    class QuotaExceededException : Exception()
    class TotalChunksExceededException : Exception()
    class UploadErrorException : Exception()
    class UploadNotTerminated(message: String) : Exception(message)
    class WrittenBytesExceededException : Exception()

    companion object {
        private val progressMutex = Mutex()

        var chunkSize: Int = 1 * 1024 * 1024 // Chunk 1 Mo
        private const val CHUNK_MAX_SIZE: Int = 50 * 1024 * 1024 // 50 Mo and max file size to upload 500Gb
        private const val OPTIMAL_TOTAL_CHUNKS: Int = 200
        private const val TOTAL_CHUNKS: Int = 10_000
        private const val MAX_TOTAL_CHUNKS_SIZE: Long = CHUNK_MAX_SIZE.toLong() * TOTAL_CHUNKS.toLong()

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
