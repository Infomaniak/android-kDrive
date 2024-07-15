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
package com.infomaniak.drive.data.services

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.NotificationUtils.BULK_DOWNLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification

class BulkDownloadWorker(context: Context, workerParams: WorkerParameters) : BaseDownloadWorker(context, workerParams) {

    private val folderId: Int by lazy { inputData.getInt(FOLDER_ID, 0) }
    private val files: List<File> by lazy {
        FileController.getFolderOfflineFilesId(folderId = folderId, UiSettings(context).sortType).map {
            val file = FileController.getFileById(it, userDrive)!!
            file
        }
    }

    private val userDrive: UserDrive by lazy {
        UserDrive(
            userId = inputData.getInt(USER_ID, AccountUtils.currentUserId),
            driveId = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        )
    }
    private val downloadOfflineFileManager by lazy {
        DownloadOfflineFileManager(
            userDrive,
            filesCount = files.size,
            downloadWorker = this,
            notificationManagerCompat
        )
    }

    override fun downloadNotification(): DownloadNotification {
        return DownloadNotification(
            titleResId = R.string.bulkDownloadNotificationTitleWithProgress,
            contentResId = R.plurals.bulkDownloadNotificationContent,
            id = BULK_DOWNLOAD_ID,
            notification = downloadProgressNotification
        )
    }

    override suspend fun downloadAction(): Result = downloadFiles()

    override fun onFinish() {
        FileController.markFilesAsOffline(filesId = files.map { it.id }, isMarkedAsOffline = false)
        clearLastDownloadedFile()
        notifyDownloadCancelled()
        notificationManagerCompat.cancel(BULK_DOWNLOAD_ID)
    }

    override fun isForOneFile() = false

    override fun workerTag() = TAG

    override val notificationTitle = context.getString(R.string.bulkDownloadNotificationTitleNoProgress)

    override val notificationId = BULK_DOWNLOAD_ID

    override fun getSizeOfDownload() = files.sumOf { it.size ?: 0L }

    private fun clearLastDownloadedFile() {
        downloadOfflineFileManager.cleanLastDownloadedFile()
    }

    private suspend fun downloadFiles(): Result {
        var result = Result.failure()

        files.forEach { fileId ->
            result = downloadOfflineFileManager.execute(applicationContext, fileId) { progress, downloadedFileId ->
                setProgressAsync(workDataOf(PROGRESS to progress, FILE_ID to downloadedFileId))
            }
        }

        if (result == Result.success()) {
            applicationContext.cancelNotification(BULK_DOWNLOAD_ID)
        }

        return result
    }

    private fun notifyDownloadCancelled() {
        Intent().apply {
            action = TAG
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(this)
        }
    }

    companion object {
        const val TAG = "BulkDownloadWorker"
        const val FOLDER_ID = "folder_id"
    }
}
