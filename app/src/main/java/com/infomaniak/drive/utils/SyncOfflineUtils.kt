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
package com.infomaniak.drive.utils

import android.content.Context
import android.net.Uri
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import io.realm.Realm
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.ensureActive
import java.util.Date
import java.io.File as IOFile

object SyncOfflineUtils {

    fun startSyncOffline(context: Context, syncOfflineFilesJob: CompletableJob) {
        DriveInfosController.getDrives(AccountUtils.currentUserId).forEach { drive ->
            val userDrive = UserDrive(driveId = drive.id)

            FileController.getRealmInstance(userDrive).use { realm ->
                val files = FileController.getOfflineFiles(null, customRealm = realm)

                files.forEach loopFiles@{ file ->
                    syncOfflineFilesJob.ensureActive()
                    if (file.isPendingOffline(context)) return@loopFiles

                    file.getOfflineFile(context, userDrive.userId)?.let { offlineFile ->
                        migrateOfflineIfNeeded(context, file, offlineFile, userDrive)

                        val apiResponse = ApiRepository.getFileDetails(file)
                        syncOfflineFilesJob.ensureActive()
                        apiResponse.data?.let { remoteFile ->
                            remoteFile.isOffline = true
                            updateFile(offlineFile, file, context, remoteFile, userDrive, realm)

                        } ?: let {
                            if (apiResponse.error?.code?.equals("object_not_found") == true) offlineFile.delete()
                        }
                    }
                }
            }
        }
    }

    private fun updateFile(
        offlineFile: IOFile,
        file: File,
        context: Context,
        remoteFile: File,
        userDrive: UserDrive,
        realm: Realm
    ) {
        if (offlineFile.lastModified() > file.getLastModifiedInMilliSecond()) {
            uploadFile(context, file, remoteFile, offlineFile, userDrive, realm)
        } else {
            downloadOfflineFile(context, file, remoteFile, offlineFile, userDrive, realm)
        }
    }

    /**
     * Migrate old offline files to the new offline structure
     */
    private fun migrateOfflineIfNeeded(context: Context, file: File, offlineFile: IOFile, userDrive: UserDrive) {
        val offlineDir = context.getString(R.string.EXPOSED_OFFLINE_DIR)
        val oldPath = IOFile(context.filesDir, "$offlineDir/${userDrive.userId}/${userDrive.driveId}/${file.id}")
        if (oldPath.exists()) oldPath.renameTo(offlineFile)
    }

    /**
     * Update the remote file with the local file
     */
    private fun uploadFile(
        context: Context,
        file: File,
        remoteFile: File,
        offlineFile: IOFile,
        userDrive: UserDrive,
        realm: Realm
    ) {
        val uri = Uri.fromFile(offlineFile)
        val fileModifiedAt = Date(offlineFile.lastModified())

        if (UploadFile.canUpload(uri, fileModifiedAt)) {
            remoteFile.lastModifiedAt = offlineFile.lastModified() / 1000
            remoteFile.size = offlineFile.length()
            FileController.updateExistingFile(newFile = remoteFile, realm = realm)
            UploadFile(
                uri = uri.toString(),
                driveId = userDrive.driveId,
                fileModifiedAt = fileModifiedAt,
                fileName = file.name,
                fileSize = offlineFile.length(),
                remoteFolder = file.parentId,
                type = UploadFile.Type.SYNC_OFFLINE.name,
                userId = userDrive.userId,
            ).store()

            context.syncImmediately()
        }
    }

    /**
     * Update the local file with the remote file
     */
    private fun downloadOfflineFile(
        context: Context,
        file: File,
        remoteFile: File,
        offlineFile: IOFile,
        userDrive: UserDrive,
        realm: Realm
    ) {
        val remoteOfflineFile = remoteFile.getOfflineFile(context, userDrive.userId) ?: return
        val pathChanged = offlineFile.path != remoteOfflineFile.path

        if (pathChanged) {
            if (file.isMedia()) file.deleteInMediaScan(context, userDrive)
            offlineFile.delete()
        }

        if (!file.isPendingOffline(context) && (!remoteFile.isOfflineAndIntact(remoteOfflineFile) || pathChanged)) {
            FileController.updateExistingFile(newFile = remoteFile, realm = realm)
            Utils.downloadAsOfflineFile(context, remoteFile, userDrive)
        }
    }

}
