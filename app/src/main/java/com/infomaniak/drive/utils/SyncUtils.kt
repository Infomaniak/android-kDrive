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
package com.infomaniak.drive.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.work.*
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.FileObserveService
import com.infomaniak.drive.data.sync.FileObserveServiceApi24
import java.util.*
import java.util.concurrent.TimeUnit

object SyncUtils {


    val DATE_TAKEN: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.DATE_TAKEN
        else "datetaken"

    fun getFileName(cursor: Cursor): String {
        return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)) ?: ""
    }

    fun getFileDates(cursor: Cursor): Pair<Date?, Date> {
        val dateTakenIndex = cursor.getColumnIndex(DATE_TAKEN)
        val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

        val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

        val fileCreatedAt = when {
            dateTakenIndex != -1 -> Date(cursor.getLong(dateTakenIndex))
            dateAddedIndex != -1 -> Date(cursor.getLong(dateAddedIndex) * 1000)
            else -> null
        }

        val fileModifiedAt = when {
            lastModifiedIndex != -1 -> Date(cursor.getLong(lastModifiedIndex))
            dateModifiedIndex != -1 -> Date(cursor.getLong(dateModifiedIndex) * 1000)
            fileCreatedAt != null -> fileCreatedAt
            else -> Date()
        }
        return Pair(fileCreatedAt, fileModifiedAt)
    }

    fun FragmentActivity.launchAllUpload(drivePermissions: DrivePermissions) {
        if (AccountUtils.isEnableAppSync() &&
            drivePermissions.checkSyncPermissions(false) &&
            UploadFile.getNotSyncFiles().isNotEmpty()
        ) {
            syncImmediately()
        }
    }

    fun Context.syncImmediately(data: Data = Data.EMPTY, force: Boolean = false) {
        if (!isSyncActive() || force) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(UploadWorker.workConstraints())
                .setInputData(data)
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork(UploadWorker.TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun Context.isSyncActive(): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            WorkQuery.Builder.fromUniqueWorkNames(arrayListOf(UploadWorker.TAG, UploadWorker.PERIODIC_TAG))
                .addStates(arrayListOf(WorkInfo.State.RUNNING))
                .build()
        ).get()?.isNotEmpty() == true
    }

    private fun Context.isAutoSyncActive(): Boolean {
        return WorkManager.getInstance(this).getWorkInfos(
            WorkQuery.Builder.fromUniqueWorkNames(arrayListOf(UploadWorker.PERIODIC_TAG))
                .addStates(arrayListOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
                .build()
        ).get()?.isNotEmpty() == true
    }

    private fun Context.startPeriodicSync(syncInterval: Long) {
        if (!isSyncActive()) {
            val request = PeriodicWorkRequestBuilder<UploadWorker>(syncInterval, TimeUnit.SECONDS)
                .setConstraints(UploadWorker.workConstraints())
                .build()
            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(UploadWorker.PERIODIC_TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }

    private fun Context.cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork(UploadWorker.TAG)
        WorkManager.getInstance(this).cancelUniqueWork(UploadWorker.PERIODIC_TAG)
    }

    fun Context.activateSyncIfNeeded() {
        UploadFile.getAppSyncSettings()?.let { syncSettings ->
            if (!isAutoSyncActive()) activateAutoSync(syncSettings)
        }
    }

    fun Context.startContentObserverService() {
        if (UploadFile.getAppSyncSettings()?.syncImmediately == true) {
            Log.d("kDrive", "start content observer!")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FileObserveServiceApi24.scheduleJob(this)
            else startService(Intent(applicationContext, FileObserveService::class.java))
        }
    }

    private fun Context.cancelContentObserver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileObserveServiceApi24.cancelJob(applicationContext)
        } else {
            applicationContext.stopService(Intent(applicationContext, FileObserveService::class.java))
        }
    }

    fun Context.activateAutoSync(syncSettings: SyncSettings) {
        cancelContentObserver()
        if (syncSettings.syncImmediately) startContentObserverService()
        startPeriodicSync(syncSettings.syncInterval)
    }

    fun Context.disableAutoSync() {
        UploadFile.deleteAllSyncFile()
        UploadFile.removeAppSyncSettings()
        cancelContentObserver()
        cancelPeriodicSync()
    }

    /**
     * Check if uri is document and retain persistable uri
     * @throws SecurityException : Doesn't retain access to the URI if the associated document is moved or deleted
     */
    fun checkDocumentProviderPermissions(context: Context, uri: Uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            context.contentResolver?.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }


}
