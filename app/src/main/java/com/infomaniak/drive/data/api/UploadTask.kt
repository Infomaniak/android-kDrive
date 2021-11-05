/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.workDataOf
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.ValidChunks
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.NotificationUtils.CURRENT_UPLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.ELAPSED_TIME
import com.infomaniak.drive.utils.NotificationUtils.uploadProgressNotification
import com.infomaniak.drive.utils.getAvailableMemory
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
            uploadTask(this)
        } catch (exception: FileNotFoundException) {
            UploadFile.deleteIfExists(uploadFile.getUriObject(), keepFile = uploadFile.isSync())
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("data", gson.toJson(uploadFile))
                Sentry.captureException(exception)
            }

        } catch (exception: Exception) {
            exception.printStackTrace()
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            throw exception
        }
    }

    private suspend fun uploadTask(coroutineScope: CoroutineScope) = withContext(Dispatchers.IO) {
        val uri = uploadFile.getUriObject()
        val fileInputStream = context.contentResolver.openInputStream(uploadFile.getOriginalUri(context))

        initChunkSize(uploadFile.fileSize)
        BufferedInputStream(fileInputStream, chunkSize).use { input ->
            val waitingCoroutines = arrayListOf<Job>()
            val requestSemaphore = Semaphore(limitParallelRequest)
            val totalChunks = ceil(uploadFile.fileSize.toDouble() / chunkSize).toInt()

            checkLimitParallelRequest()

            val uploadedChunks =
                ApiRepository.getValidChunks(uploadFile.driveId, uploadFile.remoteFolder, uploadFile.identifier).data
            val restartUpload = uploadedChunks?.let { needToResetUpload(it) } ?: false

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "Upload"
                message = "start with $totalChunks chunks and $uploadedChunks uploadedChunks"
                level = SentryLevel.INFO
            })

            Log.d("kDrive", " upload task started with total chunk: $totalChunks, valid: $uploadedChunks")

            previousChunkBytesWritten = uploadedChunks?.uploadedSize ?: 0

            for (chunkNumber in 1..totalChunks) {
                requestSemaphore.acquire()
                if (uploadedChunks?.validChunks?.contains(chunkNumber) == true && !restartUpload) {
                    Log.d("kDrive", "chunk:$chunkNumber ignored")
                    input.read(ByteArray(chunkSize), 0, chunkSize)
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

                val url = uploadUrl(
                    chunkNumber = chunkNumber,
                    conflictOption =
                    if (uploadFile.isSync() || uploadFile.isCloudStorage()) ConflictOption.REPLACE
                    else ConflictOption.RENAME,
                    currentChunkSize = count,
                    totalChunks = totalChunks
                )
                Log.d("kDrive", "Upload > Start upload ${uploadFile.fileName} to $url data size:${data.size}")

                waitingCoroutines.add(coroutineScope.uploadChunkRequest(requestSemaphore, data, url))
            }
            waitingCoroutines.joinAll()
        }

        coroutineScope.ensureActive()
        uploadNotification.apply {
            setOngoing(false)
            setContentText("100%")
            setSmallIcon(android.R.drawable.stat_sys_upload_done)
            setProgress(0, 0, false)
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }
        notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
        shareProgress(100, true)
        UploadFile.uploadFinished(uri)
    }

    private fun CoroutineScope.uploadChunkRequest(
        requestSemaphore: Semaphore,
        data: ByteArray,
        url: String
    ) = launch(Dispatchers.IO) {
        val uploadRequestBody = ProgressRequestBody(data.toRequestBody()) { currentBytes, bytesWritten, contentLength ->
            launch {
                progressMutex.withLock {
                    if (bytesWritten == contentLength) {
                        Sentry.addBreadcrumb(Breadcrumb().apply {
                            category = "Upload"
                            message = "$bytesWritten bytes were written"
                            level = SentryLevel.INFO
                        })
                    }
                    updateProgress(currentBytes, bytesWritten, contentLength, url)
                }
            }
        }

        val request = Request.Builder().url(url)
            .headers(HttpUtils.getHeaders(contentType = null))
            .put(uploadRequestBody).build()

        val response = KDriveHttpClient.getHttpClient(uploadFile.userId, 120).newCall(request).execute()
        this.manageApiResponse(response)
        requestSemaphore.release()
    }

    private fun initChunkSize(fileSize: Long) {
        val fileChunkSize = ceil(fileSize.toDouble() / TOTAL_CHUNKS).toInt()
        if (fileChunkSize > chunkSize) {
            chunkSize = fileChunkSize
        }
    }

    private fun needToResetUpload(uploadedChunks: ValidChunks): Boolean {
        if (uploadedChunks.sizeToUpload != uploadFile.fileSize) {
            uploadFile.refreshIdentifier()
            return true
        }

        return false
    }

    private fun CoroutineScope.manageApiResponse(response: Response) {
        ensureActive()
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
                when {
                    apiResponse?.error?.code.equals("file_already_exists_error") -> Unit
                    apiResponse?.error?.code.equals("lock_error") -> throw LockErrorException()
                    apiResponse?.error?.code.equals("object_not_found") -> throw FolderNotFoundException()
                    apiResponse?.error?.code.equals("quota_exceeded_error") -> throw QuotaExceededException()
                    apiResponse?.error?.code.equals("not_authorized") -> throw NotAuthorizedException()
                    apiResponse?.error?.code.equals("upload_error") -> {
                        uploadFile.refreshIdentifier()
                        throw UploadErrorException()
                    }
                    else -> throw Exception(bodyResponse)
                }
            }
        }
    }

    private fun CoroutineScope.updateProgress(currentBytes: Int, bytesWritten: Long, contentLength: Long, url: String) {
        val totalBytesWritten = currentBytes + previousChunkBytesWritten
        val progress = ((totalBytesWritten.toDouble() / uploadFile.fileSize.toDouble()) * 100).toInt()
        currentProgress = progress
        previousChunkBytesWritten += currentBytes

        if (previousChunkBytesWritten > uploadFile.fileSize) {
            uploadFile.refreshIdentifier()
            Sentry.withScope { scope ->
                scope.setExtra("data", gson.toJson(uploadFile))
                scope.setExtra("file size", "${uploadFile.fileSize}")
                scope.setExtra("uploaded size", "$previousChunkBytesWritten")
                scope.setExtra("bytesWritten", "$bytesWritten")
                scope.setExtra("contentLength", "$contentLength")
                scope.setExtra("chunk size", "$chunkSize")
                scope.setExtra("url", url)
                Sentry.captureMessage("Chunk total size exceed fileSize 😢")
            }
            Log.d(
                "UploadWorker",
                "progress >> ${uploadFile.fileName} exceed with ${uploadFile.fileSize}/${previousChunkBytesWritten}"
            )
            throw ChunksSizeExceededException()
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

    private fun checkLimitParallelRequest() {
        val availableMemory = context.getAvailableMemory().availMem
        if (chunkSize * limitParallelRequest >= availableMemory) {
            limitParallelRequest = (availableMemory / limitParallelRequest).toInt()
        }
    }

    private fun uploadUrl(
        chunkNumber: Int,
        conflictOption: ConflictOption,
        currentChunkSize: Int,
        totalChunks: Int
    ): String {
        val relativePath = if (uploadFile.remoteSubFolder == null) "" else "&relative_path=${uploadFile.remoteSubFolder}"
        return "${ApiRoutes.uploadFile(uploadFile.driveId, uploadFile.remoteFolder)}?chunk_number=$chunkNumber" +
                "&chunk_size=${chunkSize}" +
                "&current_chunk_size=$currentChunkSize" +
                "&total_chunks=$totalChunks" +
                "&total_size=${if (uploadFile.fileSize < currentChunkSize) currentChunkSize else uploadFile.fileSize}" +
                "&identifier=${uploadFile.identifier}" +
                "&file_name=${uploadFile.encodedName()}" +
                "&last_modified_at=${uploadFile.fileModifiedAt.time / 1000}" +
                "&conflict=" + conflictOption.toString() +
                relativePath +
                if (uploadFile.fileCreatedAt == null) "" else "&file_created_at=${uploadFile.fileCreatedAt!!.time / 1000}"
    }

    fun previousChunkBytesWritten() = previousChunkBytesWritten

    fun lastProgress() = currentProgress

    class ChunksSizeExceededException : Exception()
    class FolderNotFoundException : Exception()
    class LockErrorException : Exception()
    class NotAuthorizedException : Exception()
    class QuotaExceededException : Exception()
    class UploadErrorException : Exception()

    companion object {
        var chunkSize: Int = 1 * 1024 * 1024 // Chunk 1 Mo
        private val progressMutex = Mutex()
        private const val TOTAL_CHUNKS = 8000

        enum class ConflictOption {
            ERROR,
            REPLACE,
            RENAME,
            IGNORE;

            override fun toString(): String = name.lowercase()
        }
    }
}
