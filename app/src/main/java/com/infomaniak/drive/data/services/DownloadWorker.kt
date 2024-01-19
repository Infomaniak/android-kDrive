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
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import java.io.File as IOFile

class DownloadWorker(context: Context, workerParams: WorkerParameters) : BaseDownloadWorker(context, workerParams) {

    private val fileId: Int by lazy { inputData.getInt(FILE_ID, 0) }
    private val file: File? by lazy { FileController.getFileById(fileId) }
    private val offlineFile: IOFile? by lazy { file?.getOfflineFile(applicationContext, userDrive.userId) }
    private val fileName: String by lazy { inputData.getString(FILE_NAME) ?: "" }
    private val userDrive: UserDrive by lazy {
        UserDrive(
            userId = inputData.getInt(USER_ID, AccountUtils.currentUserId),
            driveId = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        )
    }
    private val downloadOfflineFileManager by lazy {
        DownloadOfflineFileManager(
            userDrive,
            0,
            this,
            notificationManagerCompat
        )
    }
    private val downloadProgressNotification by lazy {
        DownloadOfflineFileManager.createDownloadNotification(applicationContext, id, fileName)
    }
    private var notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    override fun downloadNotification(): DownloadNotification? {
        return file?.id?.let { notificationId ->
            DownloadNotification(id = notificationId, notification = downloadProgressNotification)
        }
    }

    override suspend fun downloadAction(): Result = downloadFile()

    override fun isCanceled() {
        offlineFile?.let { if (it.exists() && file?.isIntactFile(it) == false) it.delete() }
        notifyDownloadCancelled()
        Result.failure()
    }

    override fun isFinished() {
        file?.id?.let(notificationManagerCompat::cancel)
    }

    override fun isForOneFile() = true

    override fun workerTag() = TAG

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            fileId,
            DownloadOfflineFileManager.createDownloadNotification(applicationContext, id, fileName).build()
        )
    }

    private suspend fun downloadFile(): Result {
        val result = downloadOfflineFileManager.execute(applicationContext, fileId) { progress, downloadedFileId ->
            setProgressAsync(
                workDataOf(BulkDownloadWorker.PROGRESS to progress, BulkDownloadWorker.FILE_ID to downloadedFileId)
            )
        }

        if (result == Result.success()) {
            applicationContext.cancelNotification(fileId)
        }

        return result
    }

    private fun notifyDownloadCancelled() {
        Intent().apply {
            action = DownloadReceiver.TAG
            putExtra(DownloadReceiver.CANCELLED_FILE_ID, fileId)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(this)
        }
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val DRIVE_ID = "drive_id"
        const val FILE_ID = "file_id"
        const val FILE_NAME = "file_name"
        const val PROGRESS = "progress"
        const val USER_ID = "user_id"
    }
}
