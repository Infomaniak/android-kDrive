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
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.infomaniak.drive.data.models.FileInProgress
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.sync.UploadAdapter
import com.infomaniak.drive.data.sync.UploadProgressReceiver
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.NotificationUtils.CURRENT_UPLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.uploadProgressNotification
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.ApiController
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class UploadTask(
    private val context: Context,
    private val uploadFile: UploadFile,
    private val onProgress: ((progress: Int) -> Unit)? = null,
    private val supervisor: CompletableJob = SupervisorJob()
) {

    private var limitParallelRequest = 4
    private var previousChunkBytesWritten = AtomicLong(0)
    private var currentProgress = AtomicInteger(0)

    private var uploadNotification: NotificationCompat.Builder? = null
    private lateinit var notificationManagerCompat: NotificationManagerCompat

    @Throws(Exception::class)
    suspend fun start() = withContext(Dispatchers.IO + supervisor) {
        notificationManagerCompat = NotificationManagerCompat.from(context)

        uploadNotification = context.uploadProgressNotification()
        uploadNotification?.apply {
            setContentTitle(uploadFile.fileName)
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }

        try {
            uploadTask(context, uploadFile, onProgress)
        } catch (exception: Exception) {
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            throw exception
        }
    }

    @Throws(Exception::class)
    private fun CoroutineScope.uploadTask(
        context: Context,
        uploadFile: UploadFile,
        onProgress: ((progress: Int) -> Unit)? = null,
    ) {
        val uri = uploadFile.uri.toUri()
        val fileInputStream = context.contentResolver.openInputStream(uri)

        sendSyncProgress(context, UploadAdapter.ProgressStatus.STARTED, uploadFile, 0)
        initChunkSize(uploadFile.fileSize)
        BufferedInputStream(fileInputStream, chunkSize).use { input ->
            val waitingCoroutines = arrayListOf<Job>()
            val requestSemaphore = Semaphore(limitParallelRequest)
            val totalChunks = ceil(uploadFile.fileSize.toDouble() / chunkSize).toInt()

            checkLimitParallelRequest(context)

            val uploadedChunks =
                ApiRepository.getValidChunks(uploadFile.driveId, uploadFile.remoteFolder, uploadFile.identifier).data

            Log.d("kDrive", " upload task started with total chunk: $totalChunks, valid: $uploadedChunks")

            previousChunkBytesWritten.set(uploadedChunks?.uploadedSize ?: 0)

            for (chunkNumber in 1..totalChunks) {
                if (uploadedChunks?.validChunks?.contains(chunkNumber) == true) {
                    Log.d("kDrive", "chunk $chunkNumber ignored")
                    input.read(ByteArray(chunkSize))
                    continue
                }

                val coroutineRequest = launch(Dispatchers.Main) {
                    requestSemaphore.withPermit {
                        Log.i("kDrive", "Upload > ${uploadFile.fileName} chunk:$chunkNumber has permission")
                        var data = ByteArray(chunkSize)
                        val count = input.read(data)
                        data = if (count == chunkSize) data else data.copyOf(count)

                        withContext(Dispatchers.IO) {
                            val url = uploadUrl(
                                uploadFile = uploadFile,
                                chunkNumber = chunkNumber,
                                currentChunkSize = count,
                                totalChunks = totalChunks,
                            )
                            Log.d("kDrive", "Upload > Start upload ${uploadFile.fileName} to $url")

                            val uploadRequestBody =
                                ProgressRequestBody(data.toRequestBody()) { currentBytes, _, _ ->
                                    ensureActive()
                                    this@uploadTask.updateProgress(context, currentBytes, uploadFile, onProgress)
                                }

                            val request = Request.Builder().url(url)
                                .headers(HttpUtils.getHeaders(contentType = null))
                                .put(uploadRequestBody).build()

                            val response = KDriveHttpClient.getHttpClient(uploadFile.userId, 120).newCall(request).execute()
                            manageApiResponse(response)
                        }
                    }
                }
                waitingCoroutines.add(coroutineRequest)
            }
            runBlocking { waitingCoroutines.joinAll() }
        }

        ensureActive()
        uploadNotification?.apply {
            setOngoing(false)
            setContentText("100%")
            setSmallIcon(android.R.drawable.stat_sys_upload_done)
            setProgress(0, 0, false)
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }
        notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
        UploadFile.uploadFinished(uri)
        runBlocking {
            delay(150)
            sendSyncProgress(context, UploadAdapter.ProgressStatus.FINISHED, uploadFile, 100)
        }
    }

    private fun initChunkSize(fileSize: Long) {
        val fileChunkSize = ceil(fileSize.toDouble() / TOTAL_CHUNKS).toInt()
        if (fileChunkSize > chunkSize) {
            chunkSize = fileChunkSize
        }
    }

    @Throws(Exception::class)
    private fun CoroutineScope.manageApiResponse(response: Response) {
        ensureActive()
        val bodyResponse = response.body?.string()
        if (!response.isSuccessful) {
            notificationManagerCompat.cancel(CURRENT_UPLOAD_ID)
            val apiResponse = ApiController.gson.fromJson(bodyResponse, ApiResponse::class.java)
            if (apiResponse.error?.code.equals("object_not_found")) {
                throw FolderNotFoundException()
            }
            throw Exception(bodyResponse)
        }
    }

    @Synchronized
    @Throws(Exception::class)
    private fun CoroutineScope.updateProgress(
        context: Context,
        currentBytes: Int,
        uploadFile: UploadFile,
        onProgress: ((progress: Int) -> Unit)?,
    ) {
        val totalBytesWritten = currentBytes + previousChunkBytesWritten.get()
        val progress = ((totalBytesWritten.toDouble() / uploadFile.fileSize.toDouble()) * 100).toInt()
        currentProgress.set(progress)
        previousChunkBytesWritten.addAndGet(currentBytes.toLong())

        onProgress?.invoke(progress)

        ensureActive()
        uploadNotification?.apply {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.INTENT_SHOW_PROGRESS, uploadFile.remoteFolder)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            setContentIntent(pendingIntent)
            setContentText("${currentProgress}%")
            setProgress(100, currentProgress.get(), false)
            notificationManagerCompat.notify(CURRENT_UPLOAD_ID, build())
        }

        if (progress in 1..100) {
            sendSyncProgress(context, UploadAdapter.ProgressStatus.RUNNING, uploadFile, progress)
        }

        Log.i(
            "kDrive",
            " upload >> ${currentProgress}%, totalBytesWritten:$totalBytesWritten, fileSize:${uploadFile.fileSize}"
        )
    }

    private fun sendSyncProgress(
        context: Context,
        status: UploadAdapter.ProgressStatus,
        uploadFile: UploadFile,
        progress: Int = 0
    ) {
        Intent().apply {
            val fileInProgress = FileInProgress(uploadFile.remoteFolder, uploadFile.fileName, uploadFile.uri, progress, status)
            Log.d("SyncReceiver", "broadcast")
            action = UploadProgressReceiver.TAG
            putExtra(UploadAdapter.IMPORT_IN_PROGRESS, fileInProgress)
            LocalBroadcastManager.getInstance(context).sendBroadcast(this)
        }
    }

    private fun checkLimitParallelRequest(context: Context) {
        val availableMemory = context.getAvailableMemory().availMem
        if (chunkSize * limitParallelRequest >= availableMemory) {
            limitParallelRequest = (availableMemory / limitParallelRequest).toInt()
        }
    }

    private fun uploadUrl(
        chunkNumber: Int,
        currentChunkSize: Int,
        uploadFile: UploadFile,
        totalChunks: Int
    ): String {
        return "${ApiRoutes.uploadFile(uploadFile.driveId, uploadFile.remoteFolder)}?chunk_number=$chunkNumber" +
                "&chunk_size=${chunkSize}" +
                "&current_chunk_size=$currentChunkSize" +
                "&total_chunks=$totalChunks" +
                "&total_size=${uploadFile.fileSize}" +
                "&identifier=${uploadFile.identifier}" +
                "&file_name=${uploadFile.encodedName()}" +
                "&last_modified_at=${uploadFile.fileModifiedAt.time / 1000}" +
                "&conflict=replace" +
                if (uploadFile.fileCreatedAt == null) "" else "&file_created_at=${uploadFile.fileCreatedAt!!.time / 1000}"
    }

    class FolderNotFoundException : Exception()

    companion object {
        var chunkSize: Int = 1 * 1024 * 1024 // Chunk 1 Mo
        private const val TOTAL_CHUNKS = 8000
    }
}