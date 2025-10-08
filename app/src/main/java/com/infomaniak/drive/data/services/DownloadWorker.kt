/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification

class DownloadWorker(context: Context, workerParams: WorkerParameters) : BaseDownloadWorker(context, workerParams) {

    private val fileId: Int by lazy { inputData.getInt(FILE_ID, 0) }
    private val file: File? by lazy { FileController.getFileById(fileId) }
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
            filesCount = 1,
            downloadWorker = this,
            notificationManagerCompat
        )
    }

    override fun downloadNotification(): DownloadNotification? {
        return DownloadNotification(id = fileId, notification = downloadProgressNotification)
    }

    override suspend fun downloadAction(): Result = downloadFile()

    override fun onFinish(): Unit = Unit

    override fun isForOneFile() = true

    override fun workerTag() = TAG

    override val notificationTitle = fileName

    override val notificationId = fileId

    override fun getSizeOfDownload() = file?.size ?: 0L

    private suspend fun downloadFile(): Result {
        var result = Result.failure()
        file?.let {
            result = downloadOfflineFileManager.execute(applicationContext, it) { progress, downloadedFileId ->
                setProgressAsync(
                    workDataOf(PROGRESS to progress, FILE_ID to downloadedFileId)
                )
            }

            if (result == Result.failure() && fileId > 0) DownloadReceiver.notifyDownloadCancelled()
            applicationContext.cancelNotification(fileId)
        }

        return result
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val FILE_NAME = "file_name"
    }
}
