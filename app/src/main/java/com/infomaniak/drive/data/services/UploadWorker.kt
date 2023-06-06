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

import android.annotation.SuppressLint
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
import com.infomaniak.drive.data.sync.UploadNotifications.NOTIFICATION_FILES_LIMIT
import com.infomaniak.drive.data.sync.UploadNotifications.setupCurrentUploadNotification
import com.infomaniak.drive.data.sync.UploadNotifications.showUploadedFilesNotification
import com.infomaniak.drive.data.sync.UploadNotifications.syncSettingsActivityPendingIntent
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaFoldersProvider.IMAGES_BUCKET_ID
import com.infomaniak.drive.utils.MediaFoldersProvider.VIDEO_BUCKET_ID
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.uploadServiceNotification
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.getFileName
import com.infomaniak.lib.core.utils.getFileSize
import com.infomaniak.lib.core.utils.hasPermissions
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import java.util.*

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private lateinit var contentResolver: ContentResolver

    private val failedNames by lazy { inputData.getStringArray(LAST_FAILED_NAMES)?.toMutableList() ?: mutableListOf() }
    private val successNames by lazy { inputData.getStringArray(LAST_SUCCESS_NAMES)?.toMutableList() ?: mutableListOf() }
    private var failedCount = inputData.getInt(LAST_FAILED_COUNT, 0)
    private var successCount = inputData.getInt(LAST_SUCCESS_COUNT, 0)

    var currentUploadFile: UploadFile? = null
    var currentUploadTask: UploadTask? = null
    var uploadedCount = 0
    var pendingCount = 0

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
        pendingCount = uploadFiles.size

        if (pendingCount > 0) applicationContext.cancelNotification(NotificationUtils.UPLOAD_STATUS_ID)

        Log.d(TAG, "startSyncFiles> upload for ${uploadFiles.count()}")

        for (uploadFile in uploadFiles) {
            Log.d(TAG, "startSyncFiles> upload ${uploadFile.fileName}")

            if (uploadFile.initUpload(pendingCount)) {
                successNames.add(uploadFile.fileName)
                successCount++
            } else {
                failedNames.add(uploadFile.fileName)
                failedCount++
            }

            pendingCount--

            if (uploadFile.isSync() && UploadFile.getAllPendingPriorityFilesCount() > 0) break
        }

        uploadedCount = successCount

        Log.d(TAG, "startSyncFiles: finish with $uploadedCount uploaded")

        currentUploadFile?.showUploadedFilesNotification(applicationContext, successCount, successNames, failedCount, failedNames)
        if (uploadedCount > 0) Result.success() else Result.failure()
    }

    private suspend fun checkIfNeedReSync(syncSettings: SyncSettings?) {
        syncSettings?.let { checkLocalLastMedias(it) }
        if (UploadFile.getAllPendingUploadsCount() > 0) {
            val data = Data.Builder()
                .putInt(LAST_FAILED_COUNT, failedCount)
                .putInt(LAST_SUCCESS_COUNT, successCount)
                .putStringArray(LAST_FAILED_NAMES, failedNames.take(NOTIFICATION_FILES_LIMIT).toTypedArray())
                .putStringArray(LAST_SUCCESS_NAMES, successNames.take(NOTIFICATION_FILES_LIMIT).toTypedArray())
                .build()
            applicationContext.syncImmediately(data, true)
        }
    }

    private suspend fun UploadFile.initUpload(pendingCount: Int) = withContext(Dispatchers.IO) {
        val uri = getUriObject()

        currentUploadFile = this@initUpload
        applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
        updateUploadCountNotification(applicationContext, pendingCount)

        try {
            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                initUploadSchemeFile(uri)
            } else {
                initUploadSchemeContent(uri)
            }
        } catch (exception: Exception) {
            handleException(exception)
            false
        }
    }

    private suspend fun UploadFile.initUploadSchemeFile(uri: Uri): Boolean {
        val cacheFile = uri.toFile().apply {
            if (!exists()) {
                deleteIfExists()
                return false
            }
        }

        return startUploadFile(cacheFile.length()).also { isUploaded ->
            if (isUploaded) {
                deleteIfExists(keepFile = isSync())
                if (!isSyncOffline()) cacheFile.delete()
            }
        }
    }

    private suspend fun UploadFile.initUploadSchemeContent(uri: Uri): Boolean {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) startUploadFile(uri.getFileSize(cursor)) else false
        }.also { isUploaded ->
            if (isUploaded == true) deleteIfExists(keepFile = isSync())
        } ?: false
    }

    private suspend fun UploadFile.startUploadFile(size: Long): Boolean {
        return if (size != 0L) {
            if (fileSize != size) updateFileSize(size)

            currentUploadTask = UploadTask(context = applicationContext, uploadFile = this, worker = this@UploadWorker)
            currentUploadTask!!.start().also {
                Log.d(TAG, "startUploadFile> end upload $fileName")
            }

        } else {
            deleteIfExists()
            Log.d("kDrive", "$TAG > $fileName deleted size:$size")
            Sentry.withScope { scope ->
                scope.setExtra("data", ApiController.gson.toJson(this))
                scope.setExtra("fileName", fileName)
                Sentry.captureMessage("Deleted file with size 0")
            }
            false
        }
    }

    private fun UploadFile.handleException(exception: Exception) {
        when (exception) {
            is SecurityException, is IllegalStateException, is IllegalArgumentException -> {
                deleteIfExists()

                // If is an ACTION_OPEN_DOCUMENT exception and the file is older than August 17, 2022 we ignore sentry
                if (fileModifiedAt < Date(1660736262000) && exception.message?.contains("ACTION_OPEN_DOCUMENT") == true) return

                Sentry.withScope { scope ->
                    scope.setExtra("data", ApiController.gson.toJson(this))
                    if (exception is IllegalStateException) {
                        Sentry.captureMessage("The file is either partially downloaded or corrupted")
                    } else {
                        Sentry.captureException(exception)
                    }
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
        val jobs = mutableListOf<Deferred<Any?>>()
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
            customSelection = "$selection AND $IMAGES_BUCKET_ID = ? ${moreCustomConditions()}"
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
                customSelection = "$selection AND $VIDEO_BUCKET_ID = ? ${moreCustomConditions()}"

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

    private fun moreCustomConditions(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        else -> ""
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
                    val messageLog = "getLocalLastMediasAsync > from ${mediaFolder.name} ${cursor.count} found"
                    Log.d(TAG, messageLog)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = BREADCRUMB_TAG
                        message = messageLog
                        level = SentryLevel.INFO
                    })

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
        val fileName = cursor.getFileName(contentUri)
        val fileSize = uri.getFileSize(cursor)

        val messageLog = "localMediaFound > ${mediaFolder.name}/$fileName found"
        Log.d(TAG, messageLog)
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = BREADCRUMB_TAG
            message = messageLog
            level = SentryLevel.INFO
        })

        if (UploadFile.canUpload(uri, fileModifiedAt) && fileSize > 0) {
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
                deleteIfExists()
                createSubFolder(mediaFolder.name, syncSettings.createDatedSubFolders)
                store()
            }

            UploadFile.setAppSyncSettings(syncSettings.apply {
                if (fileModifiedAt > lastSync) lastSync = fileModifiedAt
            })
        }
    }

    private fun Uri.getFileSize(cursor: Cursor) = calculateFileSize(this) ?: cursor.getFileSize()

    /**
     * Calculate file size from an uri
     * The size in MediaStore is often wrong on some devices, so we calculate it
     * @param uri Uri of the file
     * @return the size result
     */
    private fun calculateFileSize(uri: Uri): Long? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                var currentSize: Int
                val byteArray = ByteArray(1024)
                var fileSize = 0L
                while (inputStream.read(byteArray).also { currentSize = it } != -1) {
                    fileSize += currentSize
                }
                fileSize
            }
        }.getOrElse { exception ->
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("uri", uri.toString())
                Sentry.captureException(exception)
            }
            null
        }
    }

    private fun syncMediaFolderFailure(exception: Throwable, contentUri: Uri, mediaFolder: MediaFolder) {
        // Catch Api>=29 for exception {Volume external_primary not found}, Adding logs to get more information
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && exception is IllegalArgumentException) {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                val volumeNames = MediaStore.getExternalVolumeNames(applicationContext).joinToString()
                scope.setExtra("uri", contentUri.toString())
                scope.setExtra("folder", mediaFolder.name)
                scope.setExtra("volume names", volumeNames)
                scope.setExtra("exception", exception.toString())
                Sentry.captureMessage("getLocalLastMediasAsync() ERROR")
            }
        } else {
            throw exception
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

        private const val LAST_FAILED_COUNT = "last_failed_count"
        private const val LAST_FAILED_NAMES = "last_failed_names"
        private const val LAST_SUCCESS_COUNT = "last_success_count"
        private const val LAST_SUCCESS_NAMES = "last_success_names"

        private const val MAX_RETRY_COUNT = 3
        private const val CHECK_LOCAL_LAST_MEDIAS_DELAY = 10_000L // 10s (in ms)

        fun workConstraints(): Constraints {
            val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
            return Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
        }

        fun uploadWorkerQuery(states: List<WorkInfo.State>): WorkQuery {
            return WorkQuery.Builder.fromUniqueWorkNames(listOf(TAG))
                .addStates(states)
                .build()
        }

        fun CoroutineScope.updateUploadCountNotification(context: Context, pendingCount: Int) {
            launch {
                // We wait a little otherwise it is too fast and the notification may not be updated
                delay(NotificationUtils.ELAPSED_TIME)
                ensureActive()
                setupCurrentUploadNotification(context, pendingCount)
            }
        }

        @SuppressLint("MissingPermission")
        fun Context.showSyncConfigNotification() {
            val pendingIntent = syncSettingsActivityPendingIntent()
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            buildGeneralNotification(getString(R.string.noSyncFolderNotificationTitle)).apply {
                setContentText(getString(R.string.noSyncFolderNotificationDescription))
                setContentIntent(pendingIntent)
                notificationManagerCompat.notify(NotificationUtils.FILE_OBSERVE_ID, this.build())
            }
        }

        fun Context.trackUploadWorkerProgress(): LiveData<MutableList<WorkInfo>> {
            return trackUploadWorker(listOf(WorkInfo.State.RUNNING))
        }

        fun Context.trackUploadWorkerSucceeded(): LiveData<MutableList<WorkInfo>> {
            return trackUploadWorker(listOf(WorkInfo.State.SUCCEEDED))
        }

        private fun Context.trackUploadWorker(states: List<WorkInfo.State>): LiveData<MutableList<WorkInfo>> {
            return WorkManager.getInstance(this).getWorkInfosLiveData(uploadWorkerQuery(states))
        }
    }
}
