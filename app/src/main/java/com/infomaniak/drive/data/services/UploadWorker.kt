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
package com.infomaniak.drive.data.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.work.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.data.sync.UploadNotifications.exceptionNotification
import com.infomaniak.drive.data.sync.UploadNotifications.folderNotFoundNotification
import com.infomaniak.drive.data.sync.UploadNotifications.lockErrorNotification
import com.infomaniak.drive.data.sync.UploadNotifications.networkErrorNotification
import com.infomaniak.drive.data.sync.UploadNotifications.outOfMemoryNotification
import com.infomaniak.drive.data.sync.UploadNotifications.quotaExceededNotification
import com.infomaniak.drive.data.sync.UploadNotifications.setupCurrentUploadNotification
import com.infomaniak.drive.data.sync.UploadNotifications.showUploadedFilesNotification
import com.infomaniak.drive.data.sync.UploadNotifications.syncSettingsActivityPendingIntent
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaFoldersProvider.IMAGES_BUCKET_ID
import com.infomaniak.drive.utils.MediaFoldersProvider.VIDEO_BUCKET_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.showGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.uploadServiceNotification
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.lib.core.utils.hasPermissions
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private lateinit var contentResolver: ContentResolver

    private var currentUploadFile: UploadFile? = null
    private var currentUploadTask: UploadTask? = null
    private var uploadedCount = 0

    override suspend fun doWork(): Result {
        contentResolver = applicationContext.contentResolver
        Log.d(TAG, "UploadWorker start job !")

        // Checks if the maximum number of retry allowed is reached
        if (runAttemptCount >= MAX_RETRY_COUNT) {
            return Result.failure()
        }

        // Move the service to foreground
        applicationContext.cancelNotification(NotificationUtils.UPLOAD_STATUS_ID)
        applicationContext.uploadServiceNotification().apply {
            setContentTitle(applicationContext.getString(R.string.notificationUploadServiceChannelName))
            setForeground(ForegroundInfo(NotificationUtils.UPLOAD_SERVICE_ID, build()))
        }

        return try {
            // Check if we have the required permissions before continuing
            if (!applicationContext.hasPermissions(DrivePermissions.permissions)) {
                UploadNotifications.permissionErrorNotification(applicationContext)
                Log.d(TAG, "UploadWorker no permissions")
                return Result.failure()
            }

            // Retrieve the latest media that have not been taken in the sync
            val appSyncSettings = UploadFile.getAppSyncSettings()
            appSyncSettings?.let {
                Log.d(TAG, "UploadWorker check locals")
                checkLocalLastMedias(it)
            }

            // Check if the user has cancelled the upload and no files to sync
            val isCancelledByUser = inputData.getBoolean(CANCELLED_BY_USER, false)
            if (UploadFile.getPendingUploadsCount() == 0L && isCancelledByUser) {
                UploadNotifications.showCancelledByUserNotification(applicationContext)
                Log.d(TAG, "UploadWorker cancelled by user")
                return Result.success()
            }

            // Start uploads
            val result = startSyncFiles()
            // Check if need to re-sync
            appSyncSettings?.let { checkIfNeedReSync(it) }

            result
        } catch (exception: UploadTask.FolderNotFoundException) {
            currentUploadFile?.folderNotFoundNotification(applicationContext)
            Result.failure()

        } catch (exception: UploadTask.QuotaExceededException) {
            currentUploadFile?.quotaExceededNotification(applicationContext)
            Result.failure()

        } catch (exception: OutOfMemoryError) {
            currentUploadFile?.outOfMemoryNotification(applicationContext)
            Result.retry()

        } catch (exception: CancellationException) { // Work has been cancelled
            Log.d(TAG, "UploadWorker > is CancellationException !")
            if (applicationContext.isSyncActive()) Result.failure()
            else Result.retry()

        } catch (exception: UploadTask.LockErrorException) {
            currentUploadFile?.lockErrorNotification(applicationContext)
            Result.retry()

        } catch (exception: UploadTask.ChunksSizeExceededException) {
            Result.retry()

        } catch (exception: Exception) {
            when {
                exception.isNetworkException() -> {
                    currentUploadFile?.networkErrorNotification(applicationContext)
                    Result.retry()
                }
                exception is IOException -> {
                    Sentry.withScope { scope ->
                        scope.setExtra("uploadFile", ApiController.gson.toJson(currentUploadFile ?: ""))
                        scope.setExtra("previousChunkBytesWritten", "${currentUploadTask?.previousChunkBytesWritten()}")
                        scope.setExtra("lastProgress", "${currentUploadTask?.lastProgress()}")
                        Sentry.captureMessage(exception.message ?: "IOException occurred")
                    }
                    Result.retry()
                }
                else -> {
                    exception.printStackTrace()
                    currentUploadFile?.exceptionNotification(applicationContext)
                    Sentry.captureException(exception)
                    Result.failure()
                }
            }
        } finally {
            applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
        }
    }

    private fun Exception.isNetworkException() =
        this.javaClass.name.contains("java.net.", ignoreCase = true) ||
                this.javaClass.name.contains("javax.net.", ignoreCase = true) ||
                this is java.io.InterruptedIOException ||
                (this is java.io.IOException && this.message == "stream closed") // Okhttp3

    private suspend fun startSyncFiles(): Result = withContext(Dispatchers.IO) {
        val syncFiles = UploadFile.getPendingUploads()
        val lastUploadedCount = inputData.getInt(LAST_UPLOADED_COUNT, 0)
        var pendingCount = syncFiles.size

        Log.d(TAG, "startSyncFiles> upload for ${syncFiles.count()}")

        syncFiles.forEach { syncFile ->
            Log.d(TAG, "startSyncFiles: upload $syncFile")
            initUploadFile(syncFile, pendingCount)
            pendingCount--
        }

        uploadedCount = syncFiles.size - pendingCount + lastUploadedCount
        if (uploadedCount > 0) currentUploadFile?.showUploadedFilesNotification(applicationContext, uploadedCount)

        Log.d(TAG, "startSyncFiles: finish with $uploadedCount uploaded")

        Result.success()
    }

    @Synchronized
    private suspend fun initUploadFile(uploadFile: UploadFile, pendingCount: Int) =
        withContext(Dispatchers.IO) {
            val uri = uploadFile.getUriObject()
            currentUploadFile = uploadFile
            applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
            uploadFile.setupCurrentUploadNotification(applicationContext, pendingCount)

            try {
                if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                    val cacheFile = uri.toFile()
                    if (!cacheFile.exists()) {
                        UploadFile.deleteIfExists(uri)
                        return@withContext
                    }
                    startUploadFile(uploadFile, cacheFile.length())
                    UploadFile.deleteIfExists(uri)
                    if (!uploadFile.isSyncOffline()) cacheFile.delete()
                } else {
                    SyncUtils.checkDocumentProviderPermissions(applicationContext, uri)

                    val fileSize = try {
                        val originalUri = uploadFile.getOriginalUri(applicationContext)
                        contentResolver.openFileDescriptor(originalUri, "r")?.use { it.statSize }
                    } catch (exception: FileNotFoundException) {
                        null
                    }
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val mediaSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                            val size = fileSize?.let { if (mediaSize > it) mediaSize else it } ?: mediaSize //TODO Temp solution
                            startUploadFile(uploadFile, size)
                        } else UploadFile.deleteIfExists(uri)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                UploadFile.deleteIfExists(uri)
            } catch (exception: IllegalStateException) {
                UploadFile.deleteIfExists(uri)
                Sentry.withScope { scope ->
                    scope.setExtra("data", ApiController.gson.toJson(uploadFile))
                    Sentry.captureMessage("The file is either partially downloaded or corrupted")
                }
            }
        }

    private suspend fun startUploadFile(uploadFile: UploadFile, size: Long) {
        if (size != 0L) {
            if (uploadFile.fileSize != size) {
                UploadFile.update(uploadFile.uri) {
                    it.fileSize = size
                    uploadFile.fileSize = size
                }
            }

            currentUploadTask = UploadTask(
                context = applicationContext,
                uploadFile = uploadFile,
                worker = this
            ).apply { start() }
            Log.d("kDrive", "$TAG > end upload ${uploadFile.fileName}")
        } else {
            UploadFile.deleteIfExists(uploadFile.getUriObject())
            Log.d("kDrive", "$TAG > ${uploadFile.fileName} deleted size:$size")
            Sentry.withScope { scope ->
                scope.setExtra("data", ApiController.gson.toJson(uploadFile))
                Sentry.captureMessage("${uploadFile.fileName} deleted size:$size")
            }
        }
    }

    private suspend fun checkIfNeedReSync(syncSettings: SyncSettings) {
        checkLocalLastMedias(syncSettings)
        if (UploadFile.getPendingUploadsCount() > 0) {
            val data = Data.Builder().putInt(LAST_UPLOADED_COUNT, uploadedCount).build()
            applicationContext.syncImmediately(data, true)
        }
    }

    private suspend fun checkLocalLastMedias(syncSettings: SyncSettings) = withContext(Dispatchers.IO) {
        val lastUploadDate = UploadFile.getLastDate(applicationContext).time - CHECK_LOCAL_LAST_MEDIAS_DELAY
        val selection = "(" + SyncUtils.DATE_TAKEN + " >= ? OR " + MediaStore.MediaColumns.DATE_ADDED + " >= ? " +
                "OR ${MediaStore.MediaColumns.DATE_MODIFIED} = ? )"
        val args = arrayOf(lastUploadDate.toString(), (lastUploadDate / 1000).toString(), (lastUploadDate / 1000).toString())
        var customSelection: String
        var customArgs: Array<String>
        val jobs = arrayListOf<Deferred<Any?>>()

        Log.d(TAG, "checkLocalLastMedias> started with ${UploadFile.getLastDate(applicationContext)}")

        MediaFolder.getAllSyncedFolders().forEach { mediaFolder ->
            Log.d(TAG, "checkLocalLastMedias> sync folder ${mediaFolder.name}_${mediaFolder.id}")
            var isNotPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "AND ${MediaStore.Images.Media.IS_PENDING} = 0" else ""
            customSelection = "$selection AND $IMAGES_BUCKET_ID = ? $isNotPending"
            customArgs = args + mediaFolder.id.toString()

            val getLastImagesOperation = getLocalLastMediasAsync(
                syncSettings = syncSettings,
                contentUri = MediaFoldersProvider.imagesExternalUri,
                selection = customSelection,
                args = customArgs,
                mediaFolder = mediaFolder
            )
            jobs.add(getLastImagesOperation)

            if (syncSettings.syncVideo) {
                isNotPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    "AND ${MediaStore.Video.Media.IS_PENDING} = 0" else ""
                customSelection = "$selection AND $VIDEO_BUCKET_ID = ? $isNotPending"

                val getLastVideosOperation = getLocalLastMediasAsync(
                    syncSettings = syncSettings,
                    contentUri = MediaFoldersProvider.videosExternalUri,
                    selection = customSelection,
                    args = customArgs,
                    mediaFolder = mediaFolder
                )
                jobs.add(getLastVideosOperation)
            }
        }

        jobs.joinAll()
    }

    private fun CoroutineScope.getLocalLastMediasAsync(
        syncSettings: SyncSettings,
        contentUri: Uri,
        selection: String,
        args: Array<String>,
        mediaFolder: MediaFolder
    ) = async {
        val sortOrder = SyncUtils.DATE_TAKEN + " ASC, " + MediaStore.MediaColumns.DATE_ADDED + " ASC, " +
                MediaStore.MediaColumns.DATE_MODIFIED + " ASC"

        contentResolver.query(contentUri, null, selection, args, sortOrder)
            ?.use { cursor ->
                Log.d(TAG, "getLocalLastMediasAsync > from ${mediaFolder.name} ${cursor.count} found")
                while (cursor.moveToNext()) {
                    val fileName = SyncUtils.getFileName(cursor)
                    val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
                    val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    val uri = cursor.uri(contentUri)
                    Log.d(TAG, "getLocalLastMediasAsync > ${mediaFolder.name}/$fileName found")

                    if (UploadFile.canUpload(uri, fileModifiedAt)) {
                        UploadFile.deleteIfExists(uri)
                        UploadFile(
                            uri = uri.toString(),
                            driveId = syncSettings.driveId,
                            fileCreatedAt = fileCreatedAt,
                            fileModifiedAt = fileModifiedAt,
                            fileName = fileName,
                            fileSize = fileSize,
                            remoteFolder = syncSettings.syncFolder,
                            userId = syncSettings.userId
                        ).apply {
                            createSubFolder(mediaFolder.name, syncSettings.createDatedSubFolders)
                            store()
                        }

                        UploadFile.setAppSyncSettings(syncSettings.apply { lastSync = fileModifiedAt })
                    }
                }
            }
    }

    companion object {
        const val TAG = "upload_worker"
        const val PERIODIC_TAG = "upload_worker_periodic"

        const val FILENAME = "filename"
        const val PROGRESS = "progress"
        const val IS_UPLOADED = "is_uploaded"
        const val REMOTE_FOLDER_ID = "remote_folder"
        const val CANCELLED_BY_USER = "cancelled_by_user"
        const val UPLOAD_FOLDER = "upload_folder"

        private const val LAST_UPLOADED_COUNT = "last_uploaded_count"

        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_LOCAL_LAST_MEDIAS_DELAY = 10000 // 10s (ms)

        fun workConstraints(): Constraints {
            val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }

        fun Context.showSyncConfigNotification() {
            val pendingIntent = this.syncSettingsActivityPendingIntent()
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            this.showGeneralNotification(getString(R.string.noSyncFolderNotificationTitle)).apply {
                setContentText(getString(R.string.noSyncFolderNotificationDescription))
                setContentIntent(pendingIntent)
                notificationManagerCompat.notify(NotificationUtils.FILE_OBSERVE_ID, this.build())
            }
        }

        fun Context.trackUploadWorkerProgress(): LiveData<MutableList<WorkInfo>> {
            return WorkManager.getInstance(this).getWorkInfosLiveData(
                WorkQuery.Builder.fromUniqueWorkNames(arrayListOf(TAG))
                    .addStates(arrayListOf(WorkInfo.State.RUNNING))
                    .build()
            )
        }
    }
}