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
package com.infomaniak.drive.utils

import android.content.Context
import android.net.Uri
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivityType
import com.infomaniak.drive.data.models.FileActivityType.FILE_MOVE_OUT
import com.infomaniak.drive.data.models.FileActivityType.FILE_RENAME
import com.infomaniak.drive.data.models.FileActivityType.FILE_RENAME_ALIAS
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.file.FileLastActivityBody
import com.infomaniak.drive.data.models.file.LastFileAction
import com.infomaniak.drive.utils.MediaUtils.deleteInMediaScan
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.Date

object SyncOfflineUtils {

    /** Maximum number of files that can be sent to the api */
    private const val API_LIMIT_FILES_ACTION_BODY = 500
    private const val API_V3_ROOT_FOLDER_NAME = "Private"
    private const val API_V3_COMMON_DOCUMENTS_NAME = "Common documents"

    private val apiV3RootFolders = setOf(API_V3_ROOT_FOLDER_NAME, API_V3_COMMON_DOCUMENTS_NAME)

    private val renameActions = setOf(FILE_RENAME, FILE_RENAME_ALIAS, FILE_MOVE_OUT)

    suspend fun startSyncOffline(context: Context) = coroutineScope {
        // Delete all offline storage files prior to APIv3. For more info, see deleteLegacyOfflineFolder kDoc
        deleteLegacyOfflineFolder(context)
        DriveInfosController.getDrives(AccountUtils.currentUserId).forEach { drive ->
            ensureActive()
            val userDrive = UserDrive(driveId = drive.id)

            FileController.getRealmInstance(userDrive).use { realm ->
                // INFO(offline): We can get remote files when the local files path is empty to prevent sync bug
                val localFiles = FileController.getOfflineFiles(order = null, customRealm = realm)

                // The api doesn't support sending a list of files that exceeds a certain limit,
                // so we chunk the files in relation to this limit.
                localFiles.chunked(API_LIMIT_FILES_ACTION_BODY).forEach {
                    ensureActive()
                    processChunk(
                        context = context,
                        coroutineScope = this,
                        userDrive = userDrive,
                        localFilesMap = it.associateBy { file -> file.id },
                        realm = realm,
                    )
                }
            }
        }
    }

    /**
     * After the migration from API V2 to API V3, offline files were saved in a different folder called "Private". Because we
     * cannot know if we're in a migration or not, we just delete old files and we marked previously isOffline files as
     * isMarkedAsOffline to let the BulkDownloadWorker redownload files.
     */
    private fun deleteLegacyOfflineFolder(context: Context) {
        val userDrive = UserDrive()
        val offlineFolder = IOFile(File.getOfflineFolder(context), "${userDrive.userId}/${userDrive.driveId}")
        offlineFolder.listFiles()?.forEach { file ->
            if (file.name !in apiV3RootFolders) file.deleteRecursively()
        }
    }

    private fun processChunk(
        context: Context,
        coroutineScope: CoroutineScope,
        userDrive: UserDrive,
        localFilesMap: Map<Int, File>,
        realm: Realm,
    ) {
        if (localFilesMap.isNotEmpty()) {
            val fileActionsBody = localFilesMap.values.map { file ->
                FileLastActivityBody.FileActionBody(
                    id = file.id,
                    fromDate = file.lastActionAt.takeIf { it > 0 } ?: file.updatedAt,
                )
            }
            val lastFilesActions = ApiRepository.getFilesLastActivities(
                driveId = userDrive.driveId,
                body = FileLastActivityBody(files = fileActionsBody),
            ).data

            // When a local file changes without a corresponding fileAction, we need to synchronize it differently.
            // We store file IDs of the processed fileActions to track what has already been handled,
            // so we only need to process files that don't have a fileAction.
            val fileActionsIds = mutableSetOf<Int>()

            lastFilesActions?.forEach { fileAction ->
                coroutineScope.ensureActive()
                fileActionsIds.add(fileAction.fileId)
                handleFileAction(context, fileAction, localFilesMap, userDrive, realm)
            }

            // Check if any of the files that don't have fileActions require synchronization.
            handleFilesWithoutActions(context, localFilesMap, fileActionsIds, userDrive, realm, coroutineScope)
        }
    }

    private fun handleFilesWithoutActions(
        context: Context,
        localFilesMap: Map<Int, File>,
        fileActionsIds: Set<Int>,
        userDrive: UserDrive,
        realm: Realm,
        coroutineScope: CoroutineScope,
    ) {
        for (file in localFilesMap.values) {
            coroutineScope.ensureActive()
            if (fileActionsIds.contains(file.id)) continue
            val ioFile = file.getOfflineFile(context, userDrive.userId) ?: continue

            migrateOfflineIfNeeded(context, file, ioFile, userDrive)

            if (ioFile.lastModified() > file.revisedAtInMillis) {
                uploadFile(
                    context = context,
                    localFile = file,
                    remoteFile = null,
                    ioFile = ioFile,
                    userDrive = userDrive,
                    realm = realm,
                )
            }
        }
    }

    private fun handleFileAction(
        context: Context,
        fileAction: LastFileAction,
        localFilesMap: Map<Int, File>,
        userDrive: UserDrive,
        realm: Realm,
    ) {
        val localFile = localFilesMap[fileAction.fileId]
        val ioFile = localFile?.getOfflineFile(context, userDrive.userId) ?: return

        migrateOfflineIfNeeded(context, localFile, ioFile, userDrive)

        when (fileAction.lastAction) {
            FileActivityType.FILE_DELETE, FileActivityType.FILE_TRASH -> ioFile.delete()
            else -> updateFile(context, ioFile, localFile, fileAction, userDrive, realm)
        }
    }

    private fun updateFile(
        context: Context,
        ioFile: IOFile,
        localFile: File,
        fileAction: LastFileAction,
        userDrive: UserDrive,
        realm: Realm,
    ) {
        val remoteFile = fileAction.file
        val ioFileLastModified = ioFile.lastModified()
        when {
            remoteFile == null -> {
                SentryLog.e("SyncOffline", "Expect remote file instead of null file") { scope ->
                    scope.setExtra("fileAction", "${fileAction.lastAction}")
                }
                return
            }
            ioFileLastModified > remoteFile.revisedAtInMillis -> {
                uploadFile(context, localFile, remoteFile, ioFile, userDrive, realm)
            }
            ioFileLastModified < remoteFile.revisedAtInMillis -> {
                downloadOfflineFile(context, localFile, remoteFile, ioFile, userDrive, realm)
            }
            fileAction.lastAction in renameActions -> {
                remoteFile.getOfflineFile(context)?.let { ioFile.renameTo(it) }
            }
        }

        FileController.updateFile(localFile.id, realm) { mutableFile ->
            mutableFile.lastActionAt = fileAction.lastActionAt ?: 0
        }
    }

    /**
     * Migrate old V1 offline files to the V2 offline structure
     */
    private fun migrateOfflineIfNeeded(context: Context, file: File, ioFile: IOFile, userDrive: UserDrive) {
        val offlineDir = context.getString(R.string.EXPOSED_OFFLINE_DIR)
        val oldPath = IOFile(context.filesDir, "$offlineDir/${userDrive.userId}/${userDrive.driveId}/${file.id}")
        if (oldPath.exists()) oldPath.renameTo(ioFile)
    }

    /**
     * Update the remote file with the local file
     */
    private fun uploadFile(
        context: Context,
        localFile: File,
        remoteFile: File?,
        ioFile: IOFile,
        userDrive: UserDrive,
        realm: Realm,
    ) {
        val uri = Uri.fromFile(ioFile)
        val fileModifiedAt = Date(ioFile.lastModified())

        if (UploadFile.canUpload(uri, fileModifiedAt)) {
            if (remoteFile != null) {
                remoteFile.lastModifiedAt = ioFile.lastModified() / 1000
                remoteFile.size = ioFile.length()
                FileController.updateExistingFile(newFile = remoteFile, realm = realm)
            }
            UploadFile(
                uri = uri.toString(),
                driveId = userDrive.driveId,
                fileModifiedAt = fileModifiedAt,
                fileName = localFile.name,
                fileSize = ioFile.length(),
                remoteFolder = localFile.parentId,
                type = UploadFile.Type.SYNC_OFFLINE.name,
                userId = userDrive.userId,
            ).store()

            context.syncImmediately(isAutomaticTrigger = false)
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
        realm: Realm,
    ) {
        val remoteOfflineFile = remoteFile.getOfflineFile(context, userDrive.userId) ?: return
        val pathChanged = offlineFile.path != remoteOfflineFile.path

        if (pathChanged) {
            if (file.isMedia()) file.deleteInMediaScan(context, userDrive)
            offlineFile.delete()
        }

        if (!file.isMarkedAsOffline && (!remoteFile.isOfflineAndIntact(remoteOfflineFile) || pathChanged)) {
            FileController.updateExistingFile(newFile = remoteFile, realm = realm)
            Utils.downloadAsOfflineFile(context, remoteFile, userDrive)
        }
    }
}
