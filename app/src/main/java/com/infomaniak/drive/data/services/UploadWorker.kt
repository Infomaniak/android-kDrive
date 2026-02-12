/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import com.infomaniak.core.legacy.utils.calculateFileSize
import com.infomaniak.core.legacy.utils.getFileName
import com.infomaniak.core.legacy.utils.getFileSize
import com.infomaniak.core.legacy.utils.hasPermissions
import com.infomaniak.core.network.api.ApiController.gson
import com.infomaniak.core.notifications.notifyCompat
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.FileChunkSizeManager.AllowedFileSizeExceededException
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UploadFile.Companion.getRealmInstance
import com.infomaniak.drive.data.services.UploadWorkerErrorHandling.runUploadCatching
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.data.sync.UploadNotifications.appendBigDescription
import com.infomaniak.drive.data.sync.UploadNotifications.showUploadedFilesNotification
import com.infomaniak.drive.data.sync.UploadNotifications.syncSettingsActivityPendingIntent
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.ForegroundInfoExt
import com.infomaniak.drive.utils.MediaFoldersProvider
import com.infomaniak.drive.utils.MediaFoldersProvider.IMAGES_BUCKET_ID
import com.infomaniak.drive.utils.MediaFoldersProvider.VIDEO_BUCKET_ID
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.UPLOAD_SERVICE_ID
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.uploadProgressNotification
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.getAvailableMemory
import com.infomaniak.drive.utils.uri
import io.realm.Realm
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.connectivityManager
import java.util.Date

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private lateinit var contentResolver: ContentResolver

    private val failedNamesMap = mutableMapOf<String, String>()
    private val successNames = mutableListOf<String>()
    private var failedCount = 0
    private var successCount = 0

    var currentUploadFile: UploadFile? = null
    var currentUploadTask: UploadTask? = null
    var uploadedCount = 0
    private val pendingUploadCounter: MutableStateFlow<Int> = MutableStateFlow(0)
    private val readMediaPermissions = DrivePermissions.permissionsFor(DrivePermissions.Type.ReadingMediaForSync).toTypedArray()
    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(applicationContext) }
    private val uploadNotificationBuilder by lazy { applicationContext.uploadProgressNotification() }

    override suspend fun doWork(): Result {

        SentryLog.d(TAG, "UploadWorker starts job!")
        contentResolver = applicationContext.contentResolver

        // Checks if the maximum number of retry allowed is reached
        if (runAttemptCount >= MAX_RETRY_COUNT) return Result.failure()

        return runUploadCatching {
            var syncNewPendingUploads: Boolean
            var result: Result
            var retryError = 0
            var lastUploadFileName = ""

            do {

                // Retrieve the latest media that have not been synced
                val appSyncSettings = when {
                    applicationContext.hasPermissions(readMediaPermissions) -> retrieveLatestNotSyncedMedia()
                    else -> null
                }

                // Check if the user has cancelled the uploads and there is no more files to sync
                checkRemainingUploadsAndUserCancellation()?.let { return@runUploadCatching it }

                // Start uploads
                result = uploadPendingFiles()

                // Check if re-sync is needed
                appSyncSettings?.let {
                    SentryLog.i(TAG, "Check if need re-sync")
                    checkLocalLastMedias(it)
                }

                syncNewPendingUploads = when {
                    applicationContext.hasPermissions(readMediaPermissions) -> UploadFile.getAllPendingUploadsCount() > 0
                    else -> UploadFile.getAllPendingPriorityFilesCount() > 0
                }

                // Update next iteration
                retryError = if (currentUploadFile?.fileName == lastUploadFileName) retryError + 1 else 0
                lastUploadFileName = currentUploadFile?.fileName ?: ""

            } while (syncNewPendingUploads && retryError < MAX_RETRY_COUNT)

            if (retryError == MAX_RETRY_COUNT) {
                SentryLog.e(TAG, "A file has been restarted several times") { scope ->
                    scope.setExtra("appSettings", gson.toJson(UploadFile.getAppSyncSettings()))
                }
                result = Result.failure()
            }

            var workFinishedMessage = "Work finished, result=$result || retryError=$retryError || lastUpload=$lastUploadFileName"
            if (SDK_INT >= 31) workFinishedMessage += " || stopReason=$stopReason"
            SentryLog.d(TAG, workFinishedMessage)

            result
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val pendingCount = pendingUploadCounter.value.takeIf { it > 0 } ?: UploadFile.getAllPendingUploadsCount()
        return progressForegroundInfo(pendingCount)
    }

    private fun reportMissingPermissionsForSync() {
        UploadNotifications.permissionErrorNotification(applicationContext)
        SentryLog.d(TAG, "UploadWorker is missing permissions to sync media")
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

    private suspend fun uploadPendingFiles(): Result = withContext(Dispatchers.IO) {


        val uploadFiles: List<UploadFile> =  initUploadPendingCounter(uploadFiles.size)
        pendingCount = uploadFiles.size
        initUploadPendingCounter(uploadFiles.size)
        val notSyncFiles = mutableListOf<UploadFile>()
        for ((index, fileToUpload) in uploadFiles.withIndex()) {
            val isLastFile = index == uploadFiles.lastIndex
            if (fileToUpload.canUpload()) {
                uploadFile(fileToUpload, isLastFile)
            } else {
                notSyncFiles += fileToUpload
            }
            pendingUploadCounter.dec()
            // Stop recursion if all files have been processed and there are only errors.
            val allNotUploadedCount = failedNamesMap.count() + notSyncFiles.count()
            if (isLastFile && allNotUploadedCount == UploadFile.getAllPendingUploadsCount()) break
            // If there is a new file during the sync and it has priority (ex: Manual uploads),
            // then we start again in order to process the priority files first.
            if (fileToUpload.isSync() && UploadFile.getAllPendingPriorityFilesCount() > 0) return@withContext uploadPendingFiles()
        }

        uploadedCount = successCount

        SentryLog.d(TAG, "uploadPendingFiles: finish with $uploadedCount uploaded")

        currentUploadFile?.showUploadedFilesNotification(
            context = applicationContext,
            successCount = successCount,
            successNames = successNames,
            failedCount = failedCount,
            failedNames = failedNamesMap.values,
        )
        if (uploadedCount > 0) Result.success() else Result.failure()
    }

    private fun UploadFile.canUpload() = when {
        !isMeteredNetwork() -> true
        isSync() -> UploadFile.getAppSyncSettings()?.onlyWifiSyncMedia == false
        isSyncOffline() -> !AppSettings.onlyWifiSyncOffline
        else -> true
	}

    private fun retrievePendingFiles(): List<UploadFile> {
        return getRealmInstance().use { realm ->
            UploadFile.getAllPendingUploads(realm).also {
                checkUploadCountReliability(realm, it.size)
            }
        }
    }

    private fun CoroutineScope.initUploadPendingCounter(size: Int) {
        if (size > 0) notificationManagerCompat.cancel(NotificationUtils.UPLOAD_STATUS_ID)
        SentryLog.d(TAG, "uploadPendingFiles> upload for $size")
    }

    private suspend fun uploadFile(uploadFile: UploadFile, isLastFile: Boolean) {
        SentryLog.d(TAG, "uploadFile> size: ${uploadFile.fileSize}")

        val canReadMedia = applicationContext.hasPermissions(readMediaPermissions)

        val fileUploadedWithSuccess = when {
            canReadMedia || uploadFile.isSync().not() -> uploadFile.upload(isLastFile)
            else -> {
                reportMissingPermissionsForSync()
                false
            }
        }

        if (fileUploadedWithSuccess) {
            SentryLog.i(TAG, "uploadFile: file uploaded with success")
            successNames.add(uploadFile.fileName)
            if (failedNamesMap[uploadFile.uri] != null) failedNamesMap.remove(uploadFile.uri)
            successCount++
        } else {
            SentryLog.i(TAG, "uploadFile: file upload failed")
            if (failedNamesMap[uploadFile.uri] == null) {
                failedNamesMap[uploadFile.uri] = uploadFile.fileName
                failedCount++
            }
        }
    }

    private fun checkUploadCountReliability(realm: Realm, pendingCount: Int) {
        val allPendingUploadsCount = UploadFile.getAllPendingUploadsCount(realm)
        if (allPendingUploadsCount != pendingCount) {
            val allPendingUploadsWithoutPriorityCount = UploadFile.getAllPendingUploadsWithoutPriorityCount(realm)
            SentryLog.i(
                tag = TAG,
                msg = "Pending uploads count change (" +
                        "pendingCount = $pendingCount, " +
                        "realmAllPendingUploadsCount = $allPendingUploadsCount, " +
                        "allPendingUploadsWithoutPriorityCount = $allPendingUploadsWithoutPriorityCount" +
                        ")"
            )

            if (pendingCount == 0) throw CancellationException("Stop several restart")
        }
    }

    private suspend fun UploadFile.upload(isLastFile: Boolean) = withContext(Dispatchers.IO) {
        val uri = getUriObject()

        currentUploadFile = this@upload
        updateUploadCountNotification()

        try {
            if (isSchemeFile()) {
                uploadSchemeFile(uri)
            } else {
                uploadSchemeContent(uri)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AllowedFileSizeExceededException) {
            SentryLog.e(TAG, "total chunks exceeded", exception) { scope ->
                scope.setExtra("half heap", "${Runtime.getRuntime().maxMemory() / 2}")
                scope.setExtra("available ram memory", "${applicationContext.getAvailableMemory().availMem}")
                scope.setExtra("available service memory", "${applicationContext.getAvailableMemory().threshold}")
            }
            if (isLastFile) throw exception
            false
        } catch (exception: Exception) {
            SentryLog.w(TAG, "upload: failed", exception)
            handleException(exception)
            false
        }
    }

    private suspend fun UploadFile.uploadSchemeFile(uri: Uri): Boolean {
        SentryLog.d(TAG, "initUploadSchemeFile: start")
        val cacheFile = uri.toFile().apply {
            if (!exists()) {
                SentryLog.i(TAG, "uploadSchemeFile: file doesn't exist")
                deleteIfExists()
                return false
            }
        }

        return startUploadFile(cacheFile.length()).also { isUploaded ->
            if (isUploaded && !isSyncOffline()) cacheFile.delete()
        }
    }

    private suspend fun UploadFile.uploadSchemeContent(uri: Uri): Boolean {
        SentryLog.d(TAG, "uploadSchemeContent: start")
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val columns = cursor.columnNames.joinToString { it }

            if (cursor.moveToFirst()) {
                if (!columns.contains(OpenableColumns.SIZE)) {
                    SentryLog.e(TAG, "uploadSchemeContent: size column doesn't exist ($columns)")
                }
                startUploadFile(uri.getFileSize(cursor))
            } else {
                val sentryMessage = "$fileName moveToFirst failed - count(${cursor.count}), columns($columns)"
                SentryLog.w(TAG, "uploadSchemeContent: $sentryMessage")
                deleteIfExists(keepFile = isSync())
                false
            }
        } ?: false
    }

    private suspend fun UploadFile.startUploadFile(size: Long): Boolean {
        if (fileSize != size) updateFileSize(size)

        SentryLog.d(TAG, "startUploadFile (size: $fileSize)")

        return UploadTask(
            context = applicationContext,
            uploadFile = this,
            setProgress = ::setProgress,
            notificationManagerCompat = notificationManagerCompat,
            uploadNotificationBuilder = uploadNotificationBuilder
        ).run {
            currentUploadTask = this
            start().also { isUploaded ->
                if (isUploaded) {
                    // If the below is true, will be deleted after the user confirms pictures deletion.
                    val toBeDeletedLater = UploadFile.getAppSyncSettings()?.deleteAfterSync == true && isSync()
                    if (!toBeDeletedLater) deleteIfExists(keepFile = isSync())
                }

                SentryLog.d(TAG, "startUploadFile> end upload file")
            }
        }
    }

    private var uploadCountNotificationJob: Job? = null
    private fun CoroutineScope.updateUploadCountNotification() {
        uploadCountNotificationJob?.cancel()
        uploadCountNotificationJob = launch {
            // We wait a little otherwise it is too fast and the notification may not be updated
            delay(NotificationUtils.ELAPSED_TIME)
            val foregroundInfo = progressForegroundInfo(pendingCount)
            setForegroundAsync(foregroundInfo)
        }
    }

                )
            }
        }
    }

    private fun progressForegroundInfo(count: Int): ForegroundInfo {
        val notification = currentUploadsNotificationBuilder.appendPendingCount(count).build()
        return ForegroundInfoExt.build(notificationId = UPLOAD_SERVICE_ID, notification = notification) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }

    private fun NotificationCompat.Builder.appendPendingCount(pendingCount: Int): NotificationCompat.Builder {
        return appendBigDescription(
            appCtx.resources.getQuantityString(
                R.plurals.uploadInProgressNumberFile,
                pendingCount,
                pendingCount
            )
        )
    }

    private fun UploadFile.handleException(exception: Exception) {
        when (exception) {
            is CancellationException -> throw exception
            is SecurityException, is IllegalStateException, is IllegalArgumentException -> {
                deleteIfExists(keepFile = isSync())

                // If is an ACTION_OPEN_DOCUMENT exception and the file is older than August 17, 2022 we ignore sentry
                if (fileModifiedAt < Date(1660736262000) && exception.message?.contains("ACTION_OPEN_DOCUMENT") == true) return

                if (exception !is IllegalStateException) Sentry.captureException(exception)
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
                "OR ${MediaStore.MediaColumns.DATE_MODIFIED} >= ? )"
        var customSelection: String
        var customArgs: Array<String>

        SentryLog.d(TAG, "checkLocalLastMedias> started with $lastUploadDate")

        getRealmInstance().use { realm ->
            MediaFolder.getAllSyncedFolders(realm).forEach { mediaFolder ->
                ensureActive()
                // Add log
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = BREADCRUMB_TAG
                    message = "sync ${mediaFolder.id}"
                    level = SentryLevel.DEBUG
                })
                SentryLog.d(TAG, "checkLocalLastMedias> sync folder ${mediaFolder.id} ${mediaFolder.name}")

                // Sync media folder
                customSelection = "$selection AND $IMAGES_BUCKET_ID = ? ${moreCustomConditions()}"
                customArgs = args + mediaFolder.id.toString()

                fetchRecentLocalMediasToSync(
                    coroutineScope = this,
                    realm = realm,
                    syncSettings = syncSettings,
                    contentUri = MediaFoldersProvider.imagesExternalUri,
                    selection = customSelection,
                    args = customArgs,
                    mediaFolder = mediaFolder,
                )

                if (syncSettings.syncVideo) {
                    customSelection = "$selection AND $VIDEO_BUCKET_ID = ? ${moreCustomConditions()}"

                    fetchRecentLocalMediasToSync(
                        coroutineScope = this,
                        realm = realm,
                        syncSettings = syncSettings,
                        contentUri = MediaFoldersProvider.videosExternalUri,
                        selection = customSelection,
                        args = customArgs,
                        mediaFolder = mediaFolder,
                    )
                }
            }
        }
    }

    private fun moreCustomConditions(): String = when {
        SDK_INT >= 30 -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
        SDK_INT >= 29 -> "AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        else -> ""
    }

    private fun fetchRecentLocalMediasToSync(
        coroutineScope: CoroutineScope,
        realm: Realm,
        syncSettings: SyncSettings,
        contentUri: Uri,
        selection: String,
        args: Array<String>,
        mediaFolder: MediaFolder,
    ) {

        val sortOrder = SyncUtils.DATE_TAKEN + " ASC, " +
                MediaStore.MediaColumns.DATE_ADDED + " ASC, " +
                MediaStore.MediaColumns.DATE_MODIFIED + " ASC"

        runCatching {
            contentResolver.query(contentUri, null, selection, args, sortOrder)
                ?.use { cursor ->
                    val messageLog = "getLocalLastMediasAsync > $contentUri from ${mediaFolder.name} ${cursor.count} found"
                    SentryLog.d(TAG, messageLog)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = BREADCRUMB_TAG
                        message = messageLog
                        level = SentryLevel.INFO
                    })

                    while (cursor.moveToNext()) {
                        coroutineScope.ensureActive()
                        processFoundLocalMedia(realm, cursor, contentUri, mediaFolder, syncSettings)
                    }
                }
        }.onFailure { exception ->
            if (SDK_INT < 29 || exception !is IllegalArgumentException) {
                throw exception
            }
        }
    }

    private fun processFoundLocalMedia(
        realm: Realm,
        cursor: Cursor,
        contentUri: Uri,
        mediaFolder: MediaFolder,
        syncSettings: SyncSettings
    ) {
        val uri = cursor.uri(contentUri)

        val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
        val fileName = cursor.getFileName(contentUri)
        val fileSize = uri.getFileSize(cursor)

        val messageLog = "localMediaFound > $fileName found in folder ${mediaFolder.name}"
        SentryLog.d(TAG, messageLog)

        if (UploadFile.canUpload(uri, fileModifiedAt, realm) && fileSize > 0) {
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
                deleteIfExists(customRealm = realm)
                createSubFolder(mediaFolder.name, syncSettings.createDatedSubFolders)
                store(customRealm = realm)
                SentryLog.i(TAG, "localMediaFound> $fileName saved in realm")
            }

            UploadFile.setAppSyncSettings(
                customRealm = realm,
                syncSettings = syncSettings.apply {
                    if (fileModifiedAt > lastSync) lastSync = fileModifiedAt
                },
            )
        } else {
            SentryLog.w(TAG, "localMediaFound> Cannot upload $fileName, size=$fileSize")
        }
    }

    private fun Uri.getFileSize(cursor: Cursor) = calculateFileSize(this) ?: cursor.getFileSize()

    private fun calculateFileSize(uri: Uri): Long? {
        return uri.calculateFileSize(contentResolver) ?: null.also {
            SentryLog.i(TAG, "Cannot calculate the file size from uri")
        }
    }

    private fun isMeteredNetwork() = runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(true)

    private fun MutableStateFlow<Int>.dec() = update { it.dec() }

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

        fun workConstraints(isAutomaticUpload: Boolean): Constraints {
            val networkType = if (isAutomaticUpload && UploadFile.getAppSyncSettings()?.onlyWifiSyncMedia == true) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
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
                notificationManagerCompat.notifyCompat(NotificationUtils.SYNC_CONFIG_ID, this)
            }
        }

        fun Context.trackUploadWorkerProgress(): LiveData<List<WorkInfo>> {
            return trackUploadWorker(listOf(WorkInfo.State.RUNNING))
        }

        fun Context.trackUploadWorkerSucceeded(): LiveData<List<WorkInfo>> {
            return trackUploadWorker(listOf(WorkInfo.State.SUCCEEDED))
        }

        private fun Context.trackUploadWorker(states: List<WorkInfo.State>): LiveData<List<WorkInfo>> {
            return WorkManager.getInstance(this).getWorkInfosLiveData(uploadWorkerQuery(states))
        }
    }
}
