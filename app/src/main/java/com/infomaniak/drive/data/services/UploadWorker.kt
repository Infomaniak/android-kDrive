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
package com.infomaniak.drive.data.services

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
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
import com.infomaniak.drive.data.services.UploadWorkerThrowable.runUploadCatching
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.data.sync.UploadNotifications.exceptionNotification
import com.infomaniak.drive.data.sync.UploadNotifications.setupCurrentUploadNotification
import com.infomaniak.drive.data.sync.UploadNotifications.showUploadedFilesNotification
import com.infomaniak.drive.data.sync.UploadNotifications.syncSettingsActivityPendingIntent
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaFoldersProvider.IMAGES_BUCKET_ID
import com.infomaniak.drive.utils.MediaFoldersProvider.VIDEO_BUCKET_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.showGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.uploadServiceNotification
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.ApiController
import com.infomaniak.lib.core.utils.hasPermissions
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import java.util.*

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private lateinit var contentResolver: ContentResolver

    var currentUploadFile: UploadFile? = null
    var currentUploadTask: UploadTask? = null
    var uploadedCount = 0

    override suspend fun doWork(): Result {

        Log.d(TAG, "UploadWorker starts job!")
        contentResolver = applicationContext.contentResolver

        // Checks if the maximum number of retry allowed is reached
        if (runAttemptCount >= MAX_RETRY_COUNT) return Result.failure()

        moveServiceToForeground()

        return runUploadCatching {
            // Check if we have the required permissions before continuing
            checkPermissions()?.let { return@runUploadCatching it }

            // Retrieve the latest media that have not been synced
            val appSyncSettings = retrieveLatestNotSyncedMedia()

            // Check if the user has cancelled the uploads and there is no more files to sync
            checkRemainingUploadsAndUserCancellation()?.let { return@runUploadCatching it }

            // Start uploads
            val result = startSyncFiles()

            // Check if re-sync is needed
            checkIfNeedReSync(appSyncSettings)

            result
        }
    }

    private suspend fun moveServiceToForeground() {
        applicationContext.uploadServiceNotification().apply {
            setContentTitle(applicationContext.getString(R.string.notificationUploadServiceChannelName))
            setForeground(ForegroundInfo(NotificationUtils.UPLOAD_SERVICE_ID, build()))
        }
    }

    private fun checkPermissions(): Result? {
        if (!applicationContext.hasPermissions(DrivePermissions.permissions)) {
            UploadNotifications.permissionErrorNotification(applicationContext)
            Log.d(TAG, "UploadWorker no permissions")
            return Result.failure()
        }
        return null
    }

    private suspend fun retrieveLatestNotSyncedMedia(): SyncSettings? {
        return UploadFile.getAppSyncSettings()?.also {
            Log.d(TAG, "UploadWorker check locals")
            checkLocalLastMedias(it)
        }
    }

    private fun checkRemainingUploadsAndUserCancellation(): Result? {
        val isCancelledByUser = inputData.getBoolean(CANCELLED_BY_USER, false)
        if (UploadFile.getAllPendingUploadsCount() == 0 && isCancelledByUser) {
            UploadNotifications.showCancelledByUserNotification(applicationContext)
            Log.d(TAG, "UploadWorker cancelled by user")
            return Result.success()
        }
        return null
    }

    private suspend fun startSyncFiles(): Result = withContext(Dispatchers.IO) {
        val uploadFiles = UploadFile.getAllPendingUploads()
        val lastUploadedCount = inputData.getInt(LAST_UPLOADED_COUNT, 0)
        var pendingCount = uploadFiles.size
        var successCount = 0

        if (pendingCount > 0) applicationContext.cancelNotification(NotificationUtils.UPLOAD_STATUS_ID)

        Log.d(TAG, "startSyncFiles> upload for ${uploadFiles.count()}")

        for (uploadFile in uploadFiles) {
            Log.d(TAG, "startSyncFiles> upload ${uploadFile.fileName}")
            if (uploadFile.initUpload(pendingCount)) successCount++
            pendingCount--
            if (UploadFile.getAllPendingPriorityFilesCount() > 0) break
        }

        uploadedCount = successCount + lastUploadedCount

        Log.d(TAG, "startSyncFiles: finish with $uploadedCount uploaded")

        if (uploadedCount > 0) {
            currentUploadFile?.showUploadedFilesNotification(applicationContext, uploadedCount)
            Result.success()
        } else {
            currentUploadFile?.exceptionNotification(applicationContext)
            Result.failure()
        }
    }

    private suspend fun checkIfNeedReSync(syncSettings: SyncSettings?) {
        syncSettings?.let { checkLocalLastMedias(it) }
        if (UploadFile.getAllPendingUploadsCount() > 0) {
            val data = Data.Builder().putInt(LAST_UPLOADED_COUNT, uploadedCount).build()
            applicationContext.syncImmediately(data, true)
        }
    }

    private suspend fun UploadFile.initUpload(pendingCount: Int) = withContext(Dispatchers.IO) {
        val uri = getUriObject()

        currentUploadFile = this@initUpload
        applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
        updateUploadCountNotification(this@initUpload, pendingCount)

        try {
            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                initUploadSchemeFile(uri)
            } else {
                initUploadSchemeContent(uri)
            }
        } catch (exception: Exception) {
            handleException(exception, uri)
            false
        }
    }

    private suspend fun UploadFile.initUploadSchemeFile(uri: Uri): Boolean {
        val cacheFile = uri.toFile().apply {
            if (!exists()) {
                UploadFile.deleteIfExists(uri)
                return false
            }
        }

        return startUploadFile(cacheFile.length()).also {
            UploadFile.deleteIfExists(uri)

            if (!isSyncOffline()) cacheFile.delete()
        }
    }

    private suspend fun UploadFile.initUploadSchemeContent(uri: Uri): Boolean {
        SyncUtils.checkDocumentProviderPermissions(applicationContext, uri)

        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val mediaSize = SyncUtils.getFileSize(cursor)
                val descriptorSize = fileDescriptorSize(getOriginalUri(applicationContext))
                val size = descriptorSize?.let { if (mediaSize > it) mediaSize else it } ?: mediaSize // TODO Temporary solution
                startUploadFile(size)
            } else {
                null
            }
        } ?: run {
            UploadFile.deleteIfExists(uri)
            false
        }
    }

    private suspend fun UploadFile.startUploadFile(size: Long): Boolean {
        return if (size != 0L) {
            if (fileSize != size) {
                UploadFile.update(uri) {
                    it.fileSize = size
                    fileSize = size
                }
            }

            currentUploadTask = UploadTask(context = applicationContext, uploadFile = this, worker = this@UploadWorker)
            currentUploadTask!!.start().also {
                Log.d(TAG, "startUploadFile> end upload $fileName")
            }

        } else {
            UploadFile.deleteIfExists(getUriObject())
            Log.d("kDrive", "$TAG > $fileName deleted size:$size")
            Sentry.withScope { scope ->
                scope.setExtra("data", ApiController.gson.toJson(this))
                Sentry.captureMessage("$fileName deleted size:$size")
            }
            false
        }
    }

    private fun UploadFile.handleException(exception: Exception, uri: Uri) {
        when (exception) {
            is SecurityException, is IllegalStateException, is IllegalArgumentException -> {
                UploadFile.deleteIfExists(uri)

                if (exception is IllegalStateException) {
                    Sentry.withScope { scope ->
                        scope.setExtra("data", ApiController.gson.toJson(this))
                        Sentry.captureMessage("The file is either partially downloaded or corrupted")
                    }
                } else {
                    Sentry.captureException(exception)
                }
            }
            else -> throw exception
        }
    }

    private suspend fun checkLocalLastMedias(syncSettings: SyncSettings) = withContext(Dispatchers.IO) {

        fun initArgs(lastUploadDate: Date): Array<String> {
            val dateInMilliSeconds = lastUploadDate.time - CHECK_LOCAL_LAST_MEDIAS_DELAY
            val dateInSeconds = (dateInMilliSeconds / 1_000L).toString()
            return arrayOf(dateInMilliSeconds.toString(), dateInSeconds, dateInSeconds)
        }

        val lastUploadDate = UploadFile.getLastDate(applicationContext)
        val args = initArgs(lastUploadDate)
        val selection = "( ${SyncUtils.DATE_TAKEN} >= ? " +
                "OR ${MediaStore.MediaColumns.DATE_ADDED} >= ? " +
                "OR ${MediaStore.MediaColumns.DATE_MODIFIED} = ? )"
        val jobs = arrayListOf<Deferred<Any?>>()
        var customSelection: String
        var customArgs: Array<String>

        Log.d(TAG, "checkLocalLastMedias> started with $lastUploadDate")

        MediaFolder.getAllSyncedFolders().forEach { mediaFolder ->
            // Add log
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = BREADCRUMB_TAG
                message = "sync ${mediaFolder.name}"
                level = SentryLevel.DEBUG
            })
            Log.d(TAG, "checkLocalLastMedias> sync folder ${mediaFolder.name}_${mediaFolder.id}")

            // Sync media folder
            var isNotPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "AND ${MediaStore.Images.Media.IS_PENDING} = 0"
            } else {
                ""
            }
            customSelection = "$selection AND $IMAGES_BUCKET_ID = ? $isNotPending"
            customArgs = args + mediaFolder.id.toString()

            val getLastImagesOperation = getLocalLastMediasAsync(
                syncSettings = syncSettings,
                contentUri = MediaFoldersProvider.imagesExternalUri,
                selection = customSelection,
                args = customArgs,
                mediaFolder = mediaFolder,
            )
            jobs.add(getLastImagesOperation)

            if (syncSettings.syncVideo) {
                isNotPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "AND ${MediaStore.Video.Media.IS_PENDING} = 0"
                } else {
                    ""
                }
                customSelection = "$selection AND $VIDEO_BUCKET_ID = ? $isNotPending"

                val getLastVideosOperation = getLocalLastMediasAsync(
                    syncSettings = syncSettings,
                    contentUri = MediaFoldersProvider.videosExternalUri,
                    selection = customSelection,
                    args = customArgs,
                    mediaFolder = mediaFolder,
                )
                jobs.add(getLastVideosOperation)
            }
        }

        jobs.joinAll()
    }

    private fun CoroutineScope.updateUploadCountNotification(uploadFile: UploadFile, pendingCount: Int) {
        launch {
            // We wait a little otherwise it is too fast and the notification may not be updated
            delay(NotificationUtils.ELAPSED_TIME)
            ensureActive()
            uploadFile.setupCurrentUploadNotification(applicationContext, pendingCount)
        }
    }

    private fun CoroutineScope.getLocalLastMediasAsync(
        syncSettings: SyncSettings,
        contentUri: Uri,
        selection: String,
        args: Array<String>,
        mediaFolder: MediaFolder
    ) = async {

        val sortOrder = SyncUtils.DATE_TAKEN + " ASC, " +
                MediaStore.MediaColumns.DATE_ADDED + " ASC, " +
                MediaStore.MediaColumns.DATE_MODIFIED + " ASC"

        runCatching {
            contentResolver.query(contentUri, null, selection, args, sortOrder)
                ?.use { cursor ->
                    Log.d(TAG, "getLocalLastMediasAsync > from ${mediaFolder.name} ${cursor.count} found")

                    while (cursor.moveToNext()) {
                        localMediaFound(cursor, contentUri, mediaFolder, syncSettings)
                    }
                }
        }.onFailure { exception ->
            syncMediaFolderFailure(exception, contentUri, mediaFolder)
        }
    }

    private fun localMediaFound(cursor: Cursor, contentUri: Uri, mediaFolder: MediaFolder, syncSettings: SyncSettings) {
        val uri = cursor.uri(contentUri)

        val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
        val fileName = SyncUtils.getFileName(cursor)
        val fileSize = fileDescriptorSize(uri) ?: SyncUtils.getFileSize(cursor)

        Log.d(TAG, "getLocalLastMediasAsync > ${mediaFolder.name}/$fileName found")

        if (fileName != null && UploadFile.canUpload(uri, fileModifiedAt) && fileSize > 0) {
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

            UploadFile.setAppSyncSettings(syncSettings.apply {
                if (fileModifiedAt > lastSync) lastSync = fileModifiedAt
            })
        }
    }

    private fun fileDescriptorSize(uri: Uri): Long? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (exception: Exception) {
            null
        }
    }

    private fun syncMediaFolderFailure(exception: Throwable, contentUri: Uri, mediaFolder: MediaFolder) {
        // Catch Api>=29 for exception {Volume external_primary not found}, Adding logs to get more information
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && exception is IllegalArgumentException) {
            Sentry.withScope { scope ->
                val volumeNames = MediaStore.getExternalVolumeNames(applicationContext).joinToString()
                scope.setExtra("uri", contentUri.toString())
                scope.setExtra("folder", mediaFolder.name)
                scope.setExtra("volume names", volumeNames)
            }
        }
    }

    companion object {
        const val TAG = "upload_worker"
        const val PERIODIC_TAG = "upload_worker_periodic"
        const val BREADCRUMB_TAG = "Upload"

        const val FILENAME = "filename"
        const val PROGRESS = "progress"
        const val IS_UPLOADED = "is_uploaded"
        const val REMOTE_FOLDER_ID = "remote_folder"
        const val CANCELLED_BY_USER = "cancelled_by_user"
        const val UPLOAD_FOLDER = "upload_folder"

        private const val LAST_UPLOADED_COUNT = "last_uploaded_count"

        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_LOCAL_LAST_MEDIAS_DELAY = 10_000L // 10s (in ms)

        fun workConstraints(): Constraints {
            val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }

        fun Context.showSyncConfigNotification() {
            val pendingIntent = syncSettingsActivityPendingIntent()
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            showGeneralNotification(getString(R.string.noSyncFolderNotificationTitle)).apply {
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

        fun Context.trackUploadWorkerSucceeded(): LiveData<MutableList<WorkInfo>> {
            return WorkManager.getInstance(this).getWorkInfosLiveData(
                WorkQuery.Builder.fromUniqueWorkNames(arrayListOf(TAG))
                    .addStates(arrayListOf(WorkInfo.State.SUCCEEDED))
                    .build()
            )
        }
    }
}
