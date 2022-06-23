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
package com.infomaniak.drive.ui.fileList

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import android.util.ArrayMap
import androidx.core.net.toFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.Utils
import io.realm.Realm
import io.realm.RealmResults
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers

class UploadInProgressViewModel(application: Application) : AndroidViewModel(application) {

    val realmUpload: Realm by lazy { UploadFile.getRealmInstance() }

    private inline val context: Context get() = getApplication<Application>().applicationContext

    fun getPendingFolders() = liveData<ArrayList<File>>(Dispatchers.IO) {
        UploadFile.getRealmInstance().use { realm ->
            UploadFile.getAllPendingFolders(realm)?.let { pendingFolders ->
                emit(generateFolderFiles(pendingFolders))
            } ?: emit(arrayListOf())
        }
    }

    fun getPendingFiles(folderId: Int) = liveData<Pair<ArrayList<File>, ArrayList<UploadFile>>?>(Dispatchers.IO) {
        val uploadFiles = UploadFile.getRealmInstance().use { realm ->
            UploadFile.getCurrentUserPendingUploads(realm, folderId)?.let { uploadFilesProxy ->
                ArrayList(realm.copyFromRealm(uploadFilesProxy, 0))
            }
        }

        uploadFiles?.let { emit(generateUploadFiles(it) to it) } ?: emit(null)
    }

    private fun generateFolderFiles(pendingFolders: RealmResults<UploadFile>): ArrayList<File> {
        val files = arrayListOf<File>()
        val drivesNames = ArrayMap<Int, String>()

        pendingFolders.forEach { uploadFile ->
            val driveId = uploadFile.driveId
            val isSharedWithMe = driveId != AccountUtils.currentDriveId

            val driveName = if (isSharedWithMe && drivesNames[driveId] == null) {
                val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, driveId, null).first()
                drivesNames[driveId] = drive.name
                drive.name

            } else {
                drivesNames[driveId]
            }

            val userDrive = UserDrive(driveId = driveId, sharedWithMe = isSharedWithMe, driveName = driveName)
            files.add(createFolderFile(uploadFile.remoteFolder, userDrive))
        }

        return files
    }

    private fun generateUploadFiles(uploadFiles: ArrayList<UploadFile>): ArrayList<File> {
        val files = arrayListOf<File>()

        uploadFiles.forEach { uploadFile ->
            val uri = uploadFile.getUriObject()

            if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                try {
                    SyncUtils.checkDocumentProviderPermissions(context, uri)
                    context.contentResolver?.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val size = SyncUtils.getFileSize(cursor)
                            files.add(
                                File(
                                    id = uploadFile.uri.hashCode(),
                                    name = uploadFile.fileName,
                                    path = uploadFile.uri,
                                    size = size,
                                    isFromUploads = true,
                                )
                            )
                        }
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    files.add(
                        File(
                            id = uploadFile.uri.hashCode(),
                            name = uploadFile.fileName,
                            path = uploadFile.uri,
                            isFromUploads = true,
                        )
                    )

                    Sentry.withScope { scope ->
                        scope.level = SentryLevel.WARNING
                        scope.setExtra("fileName", uploadFile.fileName)
                        scope.setExtra("uri", uploadFile.uri)
                        Sentry.captureException(exception)
                    }
                }
            } else {
                files.add(
                    File(
                        id = uploadFile.uri.hashCode(),
                        name = uploadFile.fileName,
                        path = uploadFile.uri,
                        size = uri.toFile().length(),
                        isFromUploads = true,
                    )
                )
            }
        }

        return files
    }

    private fun createFolderFile(fileId: Int, userDrive: UserDrive): File {
        val folder = FileController.getFileById(fileId, userDrive)!!

        val (name, type) = if (fileId == Utils.ROOT_ID) {
            Utils.getRootName(context) to File.Type.DRIVE.value
        } else {
            folder.name to File.Type.DIRECTORY.value
        }

        return File(
            id = fileId,
            name = name,
            path = folder.getRemotePath(userDrive),
            type = type,
            isFromUploads = true
        )
    }

    override fun onCleared() {
        super.onCleared()
        realmUpload.close()
    }
}