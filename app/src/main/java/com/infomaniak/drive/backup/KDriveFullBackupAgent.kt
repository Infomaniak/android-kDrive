/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalSerializationApi::class)

package com.infomaniak.drive.backup

import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.infomaniak.core.common.backup.FullBackupAgent
import com.infomaniak.core.common.backup.isDeviceToDeviceTransfer
import com.infomaniak.drive.backup.models.MediaFolderBackupModel
import com.infomaniak.drive.backup.models.SyncDbBackupModel
import com.infomaniak.drive.backup.models.SyncSettingsBackupModel
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.util.Date

class KDriveFullBackupAgent : FullBackupAgent() {

    private val backupTmpDir by lazy { filesDir.resolve("tmp_backup_agent").canonicalFile }
    private val syncDbBackupFile by lazy { backupTmpDir.resolve("sync_db.pb") }

    override fun onFullBackup(data: FullBackupDataOutput) {
        backupTmpDir.deleteRecursively() // Ensure there are no leftovers from a previous aborted backup.
        if (!data.isDeviceToDeviceTransfer) {
            backupTmpDir.mkdir()
            extractSyncDbToFile()
        }
        super.onFullBackup(data)
        backupTmpDir.deleteRecursively()
    }

    override fun onRestoreFinished() {
        backupTmpDir.deleteRecursively()
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        when (destination.canonicalFile) {
            syncDbBackupFile -> restoreSyncDb(data.toByteArray(size))
            else -> super.onRestoreFile(data, size, destination, type, mode, mtime)
        }
    }

    private fun extractSyncDbToFile() {
        val syncSettingsBackupModel = UploadFile.getAppSyncSettings()?.toBackupModel() ?: return
        val syncDbBackupModel = SyncDbBackupModel(
            settings = syncSettingsBackupModel,
            mediaFolders = MediaFolder.getAll(UploadFile.getRealmInstance()).map { it.toBackupModel() }
        )
        val bytes = ProtoBuf.encodeToByteArray(syncDbBackupModel)
        syncDbBackupFile.writeBytes(bytes)
    }

    private fun restoreSyncDb(bytes: ByteArray) {
        val backupModel = ProtoBuf.decodeFromByteArray<SyncDbBackupModel>(bytes)
        UploadFile.setAppSyncSettings(backupModel.settings.toRealmModel())
        MediaFolder.putAll(backupModel.mediaFolders.map { it.toRealmModel() })
    }
}

private fun SyncSettingsBackupModel.toRealmModel(): SyncSettings = SyncSettings(
    userId = userId,
    createDatedSubFolders = createDatedSubFolders,
    driveId = driveId,
    lastSync = Date(lastSync),
    syncFolder = syncFolder,
    syncImmediately = syncImmediately,
    syncInterval = syncInterval,
    syncVideo = syncVideo,
    deleteAfterSync = deleteAfterSync,
    onlyWifiSyncMedia = onlyWifiSyncMedia,
)

private fun SyncSettings.toBackupModel(): SyncSettingsBackupModel = SyncSettingsBackupModel(
    userId = userId,
    createDatedSubFolders = createDatedSubFolders,
    driveId = driveId,
    lastSync = Date().time, // Sync only medias created after the backup, since we are not keeping UploadFile models.
    syncFolder = syncFolder,
    syncImmediately = syncImmediately,
    syncInterval = syncInterval,
    syncVideo = syncVideo,
    deleteAfterSync = deleteAfterSync,
    onlyWifiSyncMedia = onlyWifiSyncMedia,
)

private fun MediaFolderBackupModel.toRealmModel(): MediaFolder = MediaFolder(
    id = id,
    name = name,
    isSynced = isSynced,
    path = path,
)

private fun MediaFolder.toBackupModel(): MediaFolderBackupModel = MediaFolderBackupModel(
    id = id,
    name = name,
    isSynced = isSynced,
    path = path,
)
