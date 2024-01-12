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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.BulkDownloadWorker
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.utils.NotificationUtils.downloadProgressNotification
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.util.UUID

class DownloadWorkerUtils {

    fun saveRemoteData(
        response: Response,
        outputFile: java.io.File? = null,
        outputStream: ParcelFileDescriptor.AutoCloseOutputStream? = null,
        onFinish: (() -> Unit)? = null
    ) {
        SentryLog.d(DownloadWorker.TAG, "save remote data to ${outputFile?.path}")
        BufferedInputStream(response.body?.byteStream()).use { input ->
            val stream = outputStream ?: outputFile?.outputStream()
            stream?.use { output ->
                input.copyTo(output)
                onFinish?.invoke()
            }
        }
    }

    suspend fun checkBulkDownloadStatus(
        context: Context,
        ignoreSyncOffline: Boolean,
    ) = withContext(Dispatchers.Default) {
        val workQuery = WorkQuery.Builder
            .fromUniqueWorkNames(arrayListOf(BulkDownloadWorker.TAG))
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
        downloadInterceptor: Interceptor
    ): Response {
        val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()

        return okHttpClient.newBuilder()
            .addNetworkInterceptor(downloadInterceptor).build()
            .newCall(request).execute()
    }

    fun getFileFromRemote(
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

    companion object {

        fun observeBulkDownloadOffline(context: Context) = WorkManager.getInstance(context).getWorkInfosLiveData(
            WorkQuery.Builder
                .fromUniqueWorkNames(arrayListOf(BulkDownloadWorker.TAG))
                .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
                .build()
        )
    }
}

class RemoteFileException(data: String) : Exception(data)
