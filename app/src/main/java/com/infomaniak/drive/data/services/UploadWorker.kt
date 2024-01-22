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
import com.infomaniak.drive.data.sync.UploadNotifications.showUploadedFilesNotification
import com.infomaniak.drive.data.sync.UploadNotifications.syncSettingsActivityPendingIntent
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MediaFoldersProvider.IMAGES_BUCKET_ID
import com.infomaniak.drive.utils.MediaFoldersProvider.VIDEO_BUCKET_ID
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.*
import java.util.Date

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private lateinit var contentResolver: ContentResolver

    private val failedNames = mutableListOf<String>()
    private val successNames = mutableListOf<String>()
    private var failedCount = 0
    private var successCount = 0

    var currentUploadFile: UploadFile? = null
    var currentUploadTask: UploadTask? = null
    var uploadedCount = 0
    private var pendingCount = 0

    override suspend fun doWork(): Result {

        SentryLog.d(TAG, "UploadWorker starts job!")
        contentResolver = applicationContext.contentResolver

        // Checks if the maximum number of retry allowed is reached
        if (runAttemptCount >= MAX_RETRY_COUNT) return Result.failure()

        return runUploadCatching {
            var syncNewPendingUploads = false
            var result: Result
            var retryError = 0
            var lastUploadFileName = ""

            do {
                // Check if we have the required permissions before continuing
                checkPermissions()?.let { return@runUploadCatching it }

                // Retrieve the latest media that have not been synced
                val appSyncSettings = retrieveLatestNotSyncedMedia()

                // Check if the user has cancelled the uploads and there is no more files to sync
                checkRemainingUploadsAndUserCancellation()?.let { return@runUploadCatching it }

                // Start uploads
                result = startSyncFiles()

                // Check if re-sync is needed
                appSyncSettings?.let {
                    checkLocalLastMedias(it)
                    syncNewPendingUploads = UploadFile.getAllPendingUploadsCount() > 0
                }

                // Update next iteration
                retryError = if (currentUploadFile?.fileName == lastUploadFileName) retryError + 1 else 0
                lastUploadFileName = currentUploadFile?.fileName ?: ""

            } while (syncNewPendingUploads && retryError < MAX_RETRY_COUNT)

            if (retryError == MAX_RETRY_COUNT) {
                SentryLog.wtf(TAG, "A file has been restarted several times")
                result = Result.failure()
            }

            result
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val pendingCount = if (this.pendingCount > 0) this.pendingCount else UploadFile.getAllPendingUploadsCount()
        val currentUploadNotification = UploadNotifications.getCurrentUploadNotification(applicationContext, pendingCount)
        return ForegroundInfo(NotificationUtils.UPLOAD_SERVICE_ID, currentUploadNotification.build())
    }

    private fun checkPermissions(): Result? {
        if (!applicationContext.hasPermissions(DrivePermissions.permissions)) {
            UploadNotifications.permissionErrorNotification(applicationContext)
            SentryLog.d(TAG, "UploadWorker no permissions")
            return Result.failure()
        }
        return null
    }

    private suspend fun retrieveLatestNotSyncedMedia(): SyncSettings? {
        return UploadFile.getAppSyncSettings()?.also {
            SentryLog.d(TAG, "UploadWorker check locals")
            checkLocalLastMedias(it)
        }
    }

    private fun checkRemainingUploadsAndUserCancellation(): Result? {
        val isCancelledByUser = inputData.getBoolean(CANCELLED_BY_USER, false)
        if (UploadFile.getAllPendingUploadsCount() == 0 && isCancelledByUser) {
            UploadNotifications.showCancelledByUserNotification(applicationContext)
            SentryLog.d(TAG, "UploadWorker cancelled by user")
            return Result.success()
        }
        return null
    }

    private suspend fun startSyncFiles(): Result = withContext(Dispatchers.IO) {
        val uploadFiles = UploadFile.getAllPendingUploads()
        pendingCount = uploadFiles.size

        if (pendingCount > 0) applicationContext.cancelNotification(NotificationUtils.UPLOAD_STATUS_ID)

        checkUploadCountReliability()

        SentryLog.d(TAG, "startSyncFiles> upload for ${uploadFiles.count()}")

        for (uploadFile in uploadFiles) {
            SentryLog.d(TAG, "startSyncFiles> ${uploadFile.fileName} uri: (${uploadFile.uri}) - size: ${uploadFile.fileSize}")

            if (uploadFile.initUpload()) {
                Log.i(TAG, "startSyncFiles: ${uploadFile.fileName} uploaded with success")
                successNames.add(uploadFile.fileName)
                successCount++
            } else {
                Log.i(TAG, "startSyncFiles: ${uploadFile.fileName} upload failed")
                failedNames.add(uploadFile.fileName)
                failedCount++
            }

            pendingCount--

            if (uploadFile.isSync() && UploadFile.getAllPendingPriorityFilesCount() > 0) break
        }

        uploadedCount = successCount

        SentryLog.d(TAG, "startSyncFiles: finish with $uploadedCount uploaded")

        currentUploadFile?.showUploadedFilesNotification(applicationContext, successCount, successNames, failedCount, failedNames)
        if (uploadedCount > 0) Result.success() else Result.failure()
    }

    private fun checkUploadCountReliability() {
        val allPendingUploadsCount = UploadFile.getAllPendingUploadsCount()
        if (allPendingUploadsCount != pendingCount) {
            val allPendingUploadsWithoutPriorityCount = UploadFile.getAllPendingUploadsWithoutPriority().count()
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("uploadFiles", "$pendingCount")
                scope.setExtra("realmAllPendingUploadsCount", "$allPendingUploadsCount")
                scope.setExtra("allPendingUploadsWithoutPriorityCount", "$allPendingUploadsWithoutPriorityCount")
                Sentry.captureMessage("An upload count inconsistency has been detected")
            }
            if (pendingCount == 0) throw CancellationException("Stop several restart")
        }
    }

    private suspend fun UploadFile.initUpload() = withContext(Dispatchers.IO) {
        val uri = getUriObject()

        currentUploadFile = this@initUpload
        applicationContext.cancelNotification(NotificationUtils.CURRENT_UPLOAD_ID)
        updateUploadCountNotification(applicationContext)

        try {
            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                initUploadSchemeFile(uri)
            } else {
                initUploadSchemeContent(uri)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "initUpload: $fileName failed", exception)
            handleException(exception)
            false
        }
    }

    private suspend fun UploadFile.initUploadSchemeFile(uri: Uri): Boolean {
        Log.d(TAG, "initUploadSchemeFile: $fileName start")
        val cacheFile = uri.toFile().apply {
            if (!exists()) {
                Log.i(TAG, "initUploadSchemeFile: $fileName doesn't exist")
                deleteIfExists()
                return false
            }
        }

        return startUploadFile(cacheFile.length()).also { isUploaded ->
            if (isUploaded && !isSyncOffline()) cacheFile.delete()
        }
    }

    private suspend fun UploadFile.initUploadSchemeContent(uri: Uri): Boolean {
        Log.d(TAG, "initUploadSchemeContent: $fileName start")
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val columns = cursor.columnNames.joinToString { it }

            if (cursor.moveToFirst()) {
                if (!columns.contains(OpenableColumns.SIZE)) {
                    SentryLog.e(TAG, "initUploadSchemeContent: size column doesn't exist ($columns)")
                }
                startUploadFile(uri.getFileSize(cursor))
            } else {
                Log.w(TAG, "initUploadSchemeContent: $fileName moveToFirst failed - count(${cursor.count}), columns($columns)")
                deleteIfExists(keepFile = isSync())
                false
            }
        } ?: false
    }

    private suspend fun UploadFile.startUploadFile(size: Long): Boolean {
        return if (size != 0L) {
            if (fileSize != size) updateFileSize(size)

            SentryLog.d(TAG, "startUploadFile: $fileName - size: ($fileSize)")

            UploadTask(context = applicationContext, uploadFile = this, worker = this@UploadWorker).run {
                currentUploadTask = this
                start().also { isUploaded ->
                    if (isUploaded && UploadFile.getAppSyncSettings()?.deleteAfterSync != true) {
                        deleteIfExists(keepFile = isSync())
                    }

                    SentryLog.d(TAG, "startUploadFile> end upload $fileName")
                }
            }

        } else {
            deleteIfExists()
            SentryLog.d("kDrive", "$TAG > $fileName deleted size:$size")
            Sentry.withScope { scope ->
                scope.setExtra("data", ApiController.gson.toJson(this))
                scope.setExtra("fileName", fileName)
                Sentry.captureMessage("Deleted file with size 0")
            }
            false
        }
    }

    private var uploadCountNotificationJob: Job? = null
    private fun CoroutineScope.updateUploadCountNotification(context: Context) {
        uploadCountNotificationJob?.cancel()
        uploadCountNotificationJob = launch {
            // We wait a little otherwise it is too fast and the notification may not be updated
            delay(NotificationUtils.ELAPSED_TIME)
            if (isActive) UploadNotifications.setupCurrentUploadNotification(context, pendingCount)
        }
    }

    private fun UploadFile.handleException(exception: Exception) {
        when (exception) {
            is SecurityException, is IllegalStateException, is IllegalArgumentException -> {
                deleteIfExists(keepFile = isSync())

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
        val parentJob = Job()
        var customSelection: String
        var customArgs: Array<String>

        SentryLog.d(TAG, "checkLocalLastMedias> started with $lastUploadDate")

        MediaFolder.getAllSyncedFolders().forEach { mediaFolder ->
            // Add log
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = BREADCRUMB_TAG
                message = "sync ${mediaFolder.name}"
                level = SentryLevel.DEBUG
            })
            SentryLog.d(TAG, "checkLocalLastMedias> sync folder ${mediaFolder.name}_${mediaFolder.id}")

            // Sync media folder
            customSelection = "$selection AND $IMAGES_BUCKET_ID = ? ${moreCustomConditions()}"
            customArgs = args + mediaFolder.id.toString()

            @Suppress("DeferredResultUnused")
            async(parentJob) {
                getLocalLastMedias(
                    syncSettings = syncSettings,
                    contentUri = MediaFoldersProvider.imagesExternalUri,
                    selection = customSelection,
                    args = customArgs,
                    mediaFolder = mediaFolder,
                )
            }

            if (syncSettings.syncVideo) {
                customSelection = "$selection AND $VIDEO_BUCKET_ID = ? ${moreCustomConditions()}"

                @Suppress("DeferredResultUnused")
                async(parentJob) {
                    getLocalLastMedias(
                        syncSettings = syncSettings,
                        contentUri = MediaFoldersProvider.videosExternalUri,
                        selection = customSelection,
                        args = customArgs,
                        mediaFolder = mediaFolder,
                    )
                }
            }
        }
        parentJob.complete()
        parentJob.join()
    }

    private fun moreCustomConditions(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        else -> ""
    }

    private fun getLocalLastMedias(
        syncSettings: SyncSettings,
        contentUri: Uri,
        selection: String,
        args: Array<String>,
        mediaFolder: MediaFolder
    ) {

        val sortOrder = SyncUtils.DATE_TAKEN + " ASC, " +
                MediaStore.MediaColumns.DATE_ADDED + " ASC, " +
                MediaStore.MediaColumns.DATE_MODIFIED + " ASC"

        runCatching {
            contentResolver.query(contentUri, null, selection, args, sortOrder)
                ?.use { cursor ->
                    val messageLog = "getLocalLastMediasAsync > from ${mediaFolder.name} ${cursor.count} found"
                    SentryLog.d(TAG, messageLog)
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
        SentryLog.d(TAG, messageLog)
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

    private fun calculateFileSize(uri: Uri): Long? {
        return uri.calculateFileSize(contentResolver) ?: null.also {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("uri", uri.toString())
                Sentry.captureException(Exception("Cannot calculate the file size"))
            }
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

        fun Context.showSyncConfigNotification() {
            val pendingIntent = syncSettingsActivityPendingIntent()
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            buildGeneralNotification(getString(R.string.noSyncFolderNotificationTitle)).apply {
                setContentText(getString(R.string.noSyncFolderNotificationDescription))
                setContentIntent(pendingIntent)
                notificationManagerCompat.notifyCompat(applicationContext, NotificationUtils.SYNC_CONFIG_ID, this.build())
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
