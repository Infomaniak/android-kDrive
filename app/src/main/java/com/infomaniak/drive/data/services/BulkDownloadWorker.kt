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

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.NotificationUtils.BULK_DOWNLOAD_ID
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification

class BulkDownloadWorker(context: Context, workerParams: WorkerParameters) : BaseDownloadWorker(context, workerParams) {

    private val notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    private val folderId: Int by lazy { inputData.getInt(FOLDER_ID, 0) }
    private val fileIds: List<Int> by lazy { FileController.getFolderOfflineFilesId(folderId = folderId) }
    private val userDrive: UserDrive by lazy {
        UserDrive(
            userId = inputData.getInt(USER_ID, AccountUtils.currentUserId),
            driveId = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        )
    }
    private val downloadOfflineFileManager by lazy {
        DownloadOfflineFileManager(
            userDrive,
            fileIds.size,
            this,
            notificationManagerCompat
        )
    }
    private val downloadProgressNotification by lazy {
        DownloadOfflineFileManager.createDownloadNotification(
            context = context,
            id = id,
            title = context.getString(R.string.bulkDownloadNotificationTitleNoProgress)
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

    override fun isCanceled() {
        clearLastDownloadedFile()
        notifyDownloadCancelled()
    }

    override fun isFinished() {
        notificationManagerCompat.cancel(BULK_DOWNLOAD_ID)
    }

    override fun isForOneFile() = false

    override fun workerTag() = TAG

    override suspend fun getForegroundInfo() = ForegroundInfo(BULK_DOWNLOAD_ID, downloadProgressNotification.build())

    private fun clearLastDownloadedFile() {
        downloadOfflineFileManager.cleanLastDownloadedFile()
    }

    private suspend fun downloadFiles(): Result {
        var result = Result.failure()
        fileIds.forEach { fileId ->
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
        const val DRIVE_ID = "drive_id"
        const val FILE_ID = "file_id"
        const val PROGRESS = "progress"
        const val USER_ID = "user_id"
        const val FOLDER_ID = "folder_id"
    }
}
