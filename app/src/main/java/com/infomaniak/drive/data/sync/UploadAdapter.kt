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
package com.infomaniak.drive.data.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.api.UploadTask.FolderNotFoundException
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.menu.settings.SyncSettingsActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MediaStoreUtils
import com.infomaniak.drive.utils.NotificationUtils.CURRENT_UPLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.UPLOAD_STATUS_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.NotificationUtils.uploadNotification
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.SyncUtils.isWifiConnection
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.uri
import io.sentry.Sentry
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
class UploadAdapter @JvmOverloads constructor(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean = false,
    private val contentResolver: ContentResolver = context.contentResolver
) : AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

    private lateinit var uploadSupervisorJob: CompletableJob

    private val hasUpdate = AtomicBoolean(false)
    private var syncSettings: SyncSettings? = null
    private var currentUploadFile: UploadFile? = null

    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?
    ) {
        hasUpdate.set(false)
        syncSettings = UploadFile.getAppSyncSettings()
        uploadSupervisorJob = SupervisorJob()

        try {
            // Retrieve the latest media that have not been taken in the sync
            checkLocalLastPhotos()
            // Retrieve the files to sync
            val syncFiles = UploadFile.getNotSyncFiles()
            // Continue or cancel sync
            if (checkIfNeedCancel(syncFiles, extras, syncResult)) return

            syncResult?.fullSyncRequested = true // While restart, force the sync
            // Restart if there is any data left
            checkIfNeedReSync(startSyncFiles(syncFiles, syncResult, extras))

        } catch (exception: UnknownHostException) {
            networkErrorNotification()
            restartSyncErrorWithDelay(syncResult)

        } catch (exception: FolderNotFoundException) {
            folderNotFoundException()
            restartSyncErrorWithDelay(syncResult)

        } catch (exception: InterruptedException) {
            interruptedNotification()
            restartSyncErrorWithDelay(syncResult)

        } catch (exception: OutOfMemoryError) {
            outOfMemoryNotification()
            restartSyncErrorWithDelay(syncResult)

        } catch (exception: CancellationException) {
            exceptionNotification()
            restartSyncErrorWithDelay(syncResult)

        } catch (exception: Exception) {
            if (exception.isNetworkException()) {
                networkErrorNotification()
                restartSyncErrorWithDelay(syncResult)
            } else {
                exception.printStackTrace()
                exceptionNotification()
                Sentry.captureException(exception)
                syncResult?.tooManyRetries = true // Don't retry the sync
                syncResult?.stats?.numIoExceptions = syncResult?.stats?.numIoExceptions?.plus(1)
                context.cancelNotification(CURRENT_UPLOAD_ID)
            }
        }
    }

    private fun Exception.isNetworkException() =
        this.javaClass.name.contains("java.net.", ignoreCase = true) ||
                this.javaClass.name.contains("javax.net.", ignoreCase = true) ||
                this.javaClass.name.contains("java.io.", ignoreCase = true)

    @Throws(Exception::class)
    private fun startSyncFiles(uploadFiles: ArrayList<UploadFile>, syncResult: SyncResult?, extras: Bundle?): Int {
        var pendingCount = uploadFiles.size

        runBlocking {
            uploadFiles.forEach { syncFile ->
                syncResult?.stats?.numEntries = syncResult?.stats?.numEntries?.plus(1)
                initUploadFile(syncFile, syncResult, pendingCount)
                pendingCount--
            }
        }

        val countUploadedFiles = (syncResult?.stats?.numInserts?.toInt() ?: 0) + extras?.getInt(LAST_UPLOADED_COUNT, 0)!!
        context.cancelNotification(UPLOAD_STATUS_ID)

        if (countUploadedFiles > 0) showNotification(
            context = context,
            title = context.getString(R.string.allUploadFinishedTitle),
            description = context.resources.getQuantityString(
                R.plurals.allUploadFinishedDescription,
                countUploadedFiles,
                if (countUploadedFiles == 1) currentUploadFile?.fileName else countUploadedFiles
            ),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent()
        )

        Intent().apply {
            action = UploadProgressReceiver.TAG
            putExtra(OPERATION_STATUS, ProgressStatus.FINISHED as Parcelable)
            LocalBroadcastManager.getInstance(context).sendBroadcast(this)
        }

        return countUploadedFiles
    }

    @Synchronized
    @Throws(Exception::class)
    private suspend fun initUploadFile(uploadFile: UploadFile, syncResult: SyncResult?, pendingCount: Int) {
        val uri = uploadFile.uri.toUri()
        currentUploadFile = uploadFile
        context.cancelNotification(CURRENT_UPLOAD_ID)
        setupCurrentUploadNotification(pendingCount)

        try {
            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                val cacheFile = uri.toFile()
                startUploadFile(uploadFile, cacheFile.length(), syncResult)
                UploadFile.deleteIfExists(uri)
                cacheFile.delete()
            } else {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                        startUploadFile(uploadFile, size, syncResult)
                    } else UploadFile.deleteIfExists(uri)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            UploadFile.deleteIfExists(uri)
        }
    }

    @Throws(Exception::class)
    private suspend fun startUploadFile(uploadFile: UploadFile, size: Long, syncResult: SyncResult?) {
        if (size != 0L) {
            if (uploadFile.fileSize < size) {
                UploadFile.update(uploadFile.uri) { it.fileSize = size }
            }

            UploadTask(
                context = context.applicationContext,
                uploadFile = uploadFile,
                supervisor = uploadSupervisorJob
            ).start()
            syncResult?.stats?.numInserts = syncResult?.stats?.numInserts?.plus(1)
            Log.d("kDrive", "$TAG > end upload ${uploadFile.fileName}")
        } else {
            syncResult?.stats?.numSkippedEntries = syncResult?.stats?.numSkippedEntries?.plus(1)
            UploadFile.deleteIfExists(uploadFile.uri.toUri())
            Log.d("kDrive", "$TAG > ${uploadFile.fileName} deleted size:$size")
        }
    }

    private fun setupCurrentUploadNotification(pendingCount: Int) {
        val pendingTitle = context.getString(R.string.uploadInProgressTitle)
        val pendingDescription = context.resources.getQuantityString(
            R.plurals.uploadInProgressNumberFile,
            pendingCount,
            pendingCount
        )
        showNotification(context, pendingTitle, pendingDescription, UPLOAD_STATUS_ID, progressPendingIntent())
    }

    private fun networkErrorNotification(wifiRequired: Boolean = false) {
        cancelSync()
        showNotification(
            context = context,
            title = context.getString(R.string.uploadNetworkErrorTitle),
            description = if (wifiRequired) context.getString(R.string.uploadNetworkErrorWifiRequired) else context.getString(R.string.uploadNetworkErrorDescription),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent()
        )
    }

    private fun folderNotFoundException() {
        cancelSync()
        currentUploadFile?.let { UploadFile.deleteAllByFolderId(it.remoteFolder) }
        val isSyncFile = currentUploadFile?.type == UploadFile.Type.SYNC.name
        if (isSyncFile) context?.disableAutoSync()

        val contentIntent = if (isSyncFile) PendingIntent.getActivity(
            context, 0,
            Intent(context, SyncSettingsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
        ) else null

        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.uploadFolderNotFoundError),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = contentIntent
        )
    }

    private fun interruptedNotification() {
        cancelSync()
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.anErrorHasOccurred),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent()
        )
    }

    private fun outOfMemoryNotification() {
        cancelSync()
        showNotification(
            context = context,
            title = context.getString(R.string.uploadInterruptedErrorTitle),
            description = context.getString(R.string.uploadOutOfMemoryError),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent()
        )
    }

    private fun exceptionNotification() {
        cancelSync()
        showNotification(
            context = context,
            title = context.getString(R.string.uploadErrorTitle),
            description = context.getString(R.string.anErrorHasOccurred),
            notificationId = UPLOAD_STATUS_ID,
            contentIntent = progressPendingIntent()
        )
    }

    private fun showNotification(
        context: Context,
        title: String,
        description: String,
        notificationId: Int,
        contentIntent: PendingIntent? = null
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        context.uploadNotification().apply {
            setTicker(title)
            setAutoCancel(true)
            setContentTitle(title)
            setContentText(description)
            setContentIntent(contentIntent)
            notificationManagerCompat.notify(notificationId, this.build())
        }
    }

    private fun progressPendingIntent(): PendingIntent? {
        val destination = when (AccountUtils.currentUser) {
            null -> LaunchActivity::class.java
            else -> MainActivity::class.java
        }
        val intent = Intent(context, destination).apply {
            putExtra(MainActivity.INTENT_SHOW_PROGRESS, currentUploadFile?.remoteFolder)
        }
        return PendingIntent.getActivity(
            context, 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun restartSyncErrorWithDelay(syncResult: SyncResult?) {
        syncResult?.delayUntil = (System.currentTimeMillis() / 1000) + 3 // restart in 3s but only it's possible else wait
        syncResult?.stats?.numIoExceptions = syncResult?.stats?.numIoExceptions?.plus(1) ?: 1
    }

    private fun checkIfNeedCancel(uploadFiles: ArrayList<UploadFile>, extras: Bundle?, syncResult: SyncResult?): Boolean {
        val cancelledByUser = extras?.getBoolean(CANCELLED_BY_USER, false) ?: false
        currentUploadFile = uploadFiles.firstOrNull()
        if (uploadFiles.isEmpty() && cancelledByUser) {
            showNotification(
                context = context,
                title = context.getString(R.string.uploadCancelTitle),
                description = context.getString(R.string.uploadCancelDescription),
                notificationId = UPLOAD_STATUS_ID
            )
            return true
        } // Ignore sync
        else if (uploadFiles.isNotEmpty() && AppSettings.onlyWifiSync && !context.isWifiConnection()) {
            networkErrorNotification(true)
            syncResult?.stats?.numIoExceptions = syncResult?.stats?.numIoExceptions?.plus(1)
            syncResult?.delayUntil = System.currentTimeMillis() / 1000 + 10 // restart in 10s
            return true
        } else if (uploadFiles.isEmpty()) return true
        return false
    }

    @Throws(Exception::class)
    private fun checkIfNeedReSync(lastUploadedCount: Int) {
        hasUpdate.set(false)
        if (checkLocalLastPhotos()) {
            val bundle = bundleOf(LAST_UPLOADED_COUNT to lastUploadedCount)
            context.syncImmediately(bundle)
        }
    }

    @Throws(Exception::class)
    private fun checkLocalLastPhotos(): Boolean {
        val lastUploadDate = UploadFile.getLastDate(context).time
        val selection = "(" + SyncUtils.DATE_TAKEN + " >= ? OR " + MediaStore.MediaColumns.DATE_ADDED + " >= ? )"
        val args = arrayOf(lastUploadDate.toString(), (lastUploadDate / 1000).toString())
        var customSelection: String
        var customArgs: Array<String>
        val deferreds = arrayListOf<Deferred<Any?>>()

        syncSettings?.let { syncSettings ->
            if (syncSettings.syncPicture || syncSettings.syncScreenshot) {
                when {
                    syncSettings.syncPicture && !syncSettings.syncScreenshot -> {
                        customSelection = selection +
                                " AND ${MediaStoreUtils.mediaPathColumn} like ?" +
                                " AND ${MediaStoreUtils.mediaPathColumn} not like ?"
                        customArgs = args + arrayOf("%${Environment.DIRECTORY_DCIM}%", "%${SyncUtils.DIRECTORY_SCREENSHOTS}%")
                    }

                    !syncSettings.syncPicture && syncSettings.syncScreenshot -> {
                        customSelection = selection + " AND ${MediaStoreUtils.mediaPathColumn} like ?"
                        customArgs = args + "%${SyncUtils.DIRECTORY_SCREENSHOTS}%"
                    }

                    else -> {
                        customSelection = selection +
                                " AND (${MediaStoreUtils.mediaPathColumn} like ?" +
                                " OR ${MediaStoreUtils.mediaPathColumn} like ?)"
                        customArgs = args + arrayOf("%${Environment.DIRECTORY_DCIM}%", "%${SyncUtils.DIRECTORY_SCREENSHOTS}%")
                    }
                }

                deferreds.add(getLocalLastPhotosAsync(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, customSelection, customArgs))
                deferreds.add(getLocalLastPhotosAsync(MediaStore.Images.Media.INTERNAL_CONTENT_URI, customSelection, customArgs))
            }

            if (syncSettings.syncVideo) {
                customSelection = selection + " AND ${MediaStoreUtils.mediaPathColumn} like ?"
                customArgs = args + "%${Environment.DIRECTORY_DCIM}%"

                deferreds.add(getLocalLastPhotosAsync(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, customSelection, customArgs))
                deferreds.add(getLocalLastPhotosAsync(MediaStore.Video.Media.INTERNAL_CONTENT_URI, customSelection, customArgs))
            }
            runBlocking { deferreds.joinAll() }
        }
        return hasUpdate.get()
    }

    @Throws(Exception::class)
    private fun getLocalLastPhotosAsync(contentUri: Uri, selection: String, args: Array<String>) = GlobalScope.async {
        val sortOrder = SyncUtils.DATE_TAKEN + " ASC, " + MediaStore.MediaColumns.DATE_ADDED + " ASC"
        contentResolver.query(contentUri, null, selection, args, sortOrder)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val fileName = SyncUtils.getFileName(cursor)
                    val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
                    val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                    val uri = cursor.uri(contentUri)

                    if (UploadFile.notExists(uri, fileModifiedAt)) {
                        syncSettings?.let {
                            UploadFile(
                                uri = uri.toString(),
                                driveId = it.driveId,
                                fileCreatedAt = fileCreatedAt,
                                fileModifiedAt = fileModifiedAt,
                                fileName = fileName,
                                fileSize = fileSize,
                                remoteFolder = it.syncFolder,
                                userId = it.userId
                            ).store()
                            syncSettings?.let { syncSettings ->
                                UploadFile.setAppSyncSettings(syncSettings.apply { lastSync = fileModifiedAt })
                            }
                            if (!hasUpdate.get()) hasUpdate.set(true)
                        }
                    }
                }
            }
    }

    private fun cancelSync() {
        uploadSupervisorJob.cancelChildren()
        uploadSupervisorJob.cancel()
        context.cancelNotification(CURRENT_UPLOAD_ID)
        context.cancelNotification(UPLOAD_STATUS_ID)
    }

    override fun onSyncCanceled() {
        Log.d("kDrive", "$TAG > sync cancelled")
        cancelSync()
        super.onSyncCanceled()
    }

    @Parcelize
    enum class ProgressStatus : Parcelable { PENDING, STARTED, RUNNING, FINISHED }
    companion object {
        const val TAG = "SyncAdapter"

        const val CANCELLED_BY_USER = "cancelled_by_user"
        const val OPERATION_STATUS = "progress_status"
        const val IMPORT_IN_PROGRESS = "import_in_progress"
        private const val LAST_UPLOADED_COUNT = "last_uploaded_count"
    }
}
