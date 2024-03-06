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
package com.infomaniak.drive.utils

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ProgressResponseBody
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.BaseDownloadWorker
import com.infomaniak.drive.data.services.BulkDownloadWorker
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.NotificationUtils.downloadProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.UUID

class DownloadOfflineFileManager(
    private val userDrive: UserDrive,
    private val filesCount: Int,
    private val downloadWorker: BaseDownloadWorker,
    private val notificationManagerCompat: NotificationManagerCompat
) {
    var lastDownloadedFile: IOFile? = null
    var filesDownloaded = 0

    private var result: ListenableWorker.Result = ListenableWorker.Result.failure()
    private var lastUpdateProgressMillis = System.currentTimeMillis()

    suspend fun execute(
        context: Context,
        fileId: Int,
        onProgress: (progress: Int, fileId: Int) -> Unit,
    ): ListenableWorker.Result {
        val file = FileController.getFileById(fileId, userDrive)
        val offlineFile = file?.getOfflineFile(context, userDrive.userId)
        val cacheFile = file?.getCacheFile(context, userDrive)

        offlineFile?.let {
            if (file.isOfflineAndIntact(it)) {
                // We can have this case for example when we try to put a lot of files at once in offline mode
                // and for some reason, the worker is cancelled after a long time, the worker is restarted
                filesDownloaded += 1
                lastDownloadedFile = offlineFile
                return ListenableWorker.Result.success()
            }
        }

        onProgress(0, fileId)

        if (offlineFile?.exists() == true) offlineFile.delete()
        if (cacheFile?.exists() == true) cacheFile.delete()

        if (file == null || offlineFile == null) {
            getFileFromRemote(context, fileId, userDrive) { downloadedFile ->
                downloadedFile.getOfflineFile(context, userDrive.driveId)?.let { offlineFile ->
                    lastDownloadedFile = offlineFile
                }
            }
        } else {
            lastDownloadedFile = offlineFile
        }

        result = file?.let {
            startOfflineDownload(context = context, file = file, offlineFile = offlineFile!!, onProgress = onProgress)
        } ?: ListenableWorker.Result.failure()
        return result
    }

    fun cleanLastDownloadedFile() {
        if (lastDownloadedFile?.exists() == true) lastDownloadedFile?.delete()
    }

    private fun getFileFromRemote(
        context: Context,
        fileId: Int,
        userDrive: UserDrive = UserDrive(),
        onFileDownloaded: (downloadedFile: File) -> Unit
    ) {
        val fileDetails = ApiRepository.getFileDetails(File(id = fileId, driveId = userDrive.driveId))
        val remoteFile = fileDetails.data
        val file = if (fileDetails.isSuccess() && remoteFile != null) {
            FileController.getRealmInstance(userDrive).use { realm ->
                FileController.updateExistingFile(newFile = remoteFile, realm = realm)
            }
            remoteFile
        } else {
            if (fileDetails.error?.exception is ApiController.NetworkException) throw UploadTask.NetworkException()

            val translatedError = fileDetails.translatedError
            val responseGsonType = object : TypeToken<ApiResponse<File>>() {}.type
            val translatedErrorText = if (translatedError == 0) "" else context.getString(translatedError)
            val responseJson = ApiController.gson.toJson(fileDetails, responseGsonType)
            throw RemoteFileException("$responseJson $translatedErrorText")
        }
        onFileDownloaded.invoke(file)
    }

    private suspend fun startOfflineDownload(
        context: Context,
        file: File,
        offlineFile: java.io.File,
        onProgress: (progress: Int, fileId: Int) -> Unit
    ): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val okHttpClient = AccountUtils.getHttpClient(userDrive.userId, null)
        val response = downloadFileResponse(
            fileUrl = ApiRoutes.downloadFile(file),
            okHttpClient = okHttpClient,
            downloadInterceptor = downloadProgressInterceptor(
                getMostRecentLastUpdate = { lastUpdateProgressMillis },
                onLastUpdateChange = { lastUpdate -> lastUpdateProgressMillis = lastUpdate },
                onProgress = { progress ->
                    ensureActive()

                    onProgress(progress, file.id)

                    if (downloadWorker.isForOneFile()) {
                        updateDownloadNotification(
                            context = context,
                            contentTitle = file.name,
                            contentText = "%d%%".format(progress),
                            progressPercent = progress
                        )
                    }

                    SentryLog.d(downloadWorker.workerTag(), "download $progress%")
                }
            )
        )

        saveRemoteData(downloadWorker.workerTag(), response, offlineFile) {
            onProgress(100, file.id)
            FileController.updateOfflineStatus(file.id, true)
            offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
            if (file.isMedia()) MediaUtils.scanFile(context, offlineFile)
        }

        if (response.isSuccessful) {
            fileDownloaded(context)
            ListenableWorker.Result.success()
        } else ListenableWorker.Result.failure()
    }

    private fun fileDownloaded(context: Context) {
        lastUpdateProgressMillis = System.currentTimeMillis()
        filesDownloaded += 1

        val progressPercent = (filesDownloaded * 100) / filesCount
        downloadWorker.downloadNotification()?.let { notification ->
            val notificationContentTitle = notification.titleResId?.let { titleResId ->
                context.getString(titleResId, progressPercent)
            }
            val notificationContentText = notification.contentResId?.let { contentResId ->
                context.resources.getQuantityString(
                    contentResId,
                    filesDownloaded,
                    filesDownloaded,
                    filesCount
                )
            }
            updateDownloadNotification(
                context = context,
                contentTitle = notificationContentTitle,
                contentText = notificationContentText,
                progressPercent = progressPercent
            )
        }
    }

    private fun updateDownloadNotification(
        context: Context,
        contentTitle: String?,
        contentText: String?,
        progressPercent: Int
    ) {
        val downloadNotification = downloadWorker.downloadNotification()
        downloadNotification?.notification?.apply {
            setContentTitle(contentTitle)
            setContentText(contentText)
            setProgress(100, progressPercent, false)
            notificationManagerCompat.notifyCompat(context, downloadNotification.id, build())
        }
    }

    companion object {
        private const val MAX_INTERVAL_BETWEEN_PROGRESS_UPDATE_MS = 1000L

        fun observeBulkDownloadOffline(context: Context) = WorkManager.getInstance(context).getWorkInfosLiveData(
            WorkQuery.Builder
                .fromUniqueWorkNames(arrayListOf(BulkDownloadWorker.TAG))
                .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
                .build()
        )

        suspend fun checkWorkerDownloadStatus(
            context: Context,
            ignoreSyncOffline: Boolean,
            workerName: String,
        ) = withContext(Dispatchers.Default) {
            val workQuery = WorkQuery.Builder
                .fromUniqueWorkNames(arrayListOf(workerName))
                .addStates(arrayListOf(WorkInfo.State.RUNNING))
                .build()
            val workInfoList = WorkManager.getInstance(context).getWorkInfos(workQuery).await()
            val isRunning = workInfoList.isNotEmpty() && workInfoList.first().state == WorkInfo.State.RUNNING

            !(!isRunning && !ignoreSyncOffline)
        }

        fun createDownloadNotification(context: Context, id: UUID, title: String): NotificationCompat.Builder {
            val cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
            val cancelAction = NotificationCompat.Action(
                /* icon = */ null,
                /* title = */ context.getString(R.string.buttonCancel),
                /* intent = */ cancelPendingIntent
            )
            return context.downloadProgressNotification().apply {
                setOngoing(true)
                setContentTitle(title)
                addAction(cancelAction)
            }
        }

        fun downloadFileResponse(
            fileUrl: String,
            okHttpClient: OkHttpClient = HttpClient.okHttpClient,
            downloadInterceptor: Interceptor? = null
        ): Response {
            val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()

            return okHttpClient.newBuilder().apply {
                downloadInterceptor?.let {  interceptor -> addInterceptor(interceptor) }
            }.build().newCall(request).execute()
        }

        fun saveRemoteData(
            workerTag: String,
            response: Response,
            outputFile: java.io.File? = null,
            outputStream: ParcelFileDescriptor.AutoCloseOutputStream? = null,
            onFinish: (() -> Unit)? = null
        ) {
            SentryLog.d(workerTag, "save remote data to ${outputFile?.path}")
            response.body?.byteStream()?.buffered()?.use { input ->
                val stream = outputStream ?: outputFile?.outputStream()
                stream?.use { output ->
                    input.copyTo(output)
                    onFinish?.invoke()
                }
            }
        }

        fun downloadProgressInterceptor(
            getMostRecentLastUpdate: (() -> Long)? = null,
            onLastUpdateChange: ((lastUpdateValue: Long) -> Unit)? = null,
            onProgress: (progress: Int) -> Unit
        ) = Interceptor { chain: Interceptor.Chain ->
            val originalResponse = chain.proceed(chain.request())

            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body!!, object : ProgressResponseBody.ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        val currentSystemTimeMillis = System.currentTimeMillis()
                        val lastUpdate = getMostRecentLastUpdate?.invoke() ?: 0L
                        if (currentSystemTimeMillis - lastUpdate > MAX_INTERVAL_BETWEEN_PROGRESS_UPDATE_MS) {
                            onLastUpdateChange?.invoke(currentSystemTimeMillis)
                            val progress = (bytesRead.toFloat() / contentLength.toFloat() * 100F).toInt()
                            onProgress.invoke(progress)
                        }
                    }
                })).build()
        }
    }
}

class RemoteFileException(data: String) : Exception(data)
