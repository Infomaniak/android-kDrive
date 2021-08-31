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
package com.infomaniak.drive.data.models

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.infomaniak.drive.data.sync.UploadMigration
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.lib.core.utils.format
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.annotations.PrimaryKey
import java.io.File
import java.net.URLEncoder
import java.util.*

open class UploadFile(
    @PrimaryKey var uri: String = "",
    var deletedAt: Date? = null,
    var driveId: Int = -1,
    var fileCreatedAt: Date? = null,
    var fileModifiedAt: Date = Date(),
    var fileName: String = "",
    var fileSize: Long = 0L,
    var identifier: String = UUID.randomUUID().toString(),
    var remoteFolder: Int = -1,
    var remoteSubFolder: String? = null,
    var type: String = Type.SYNC.name,
    var uploadAt: Date? = null,
    var userId: Int = -1
) : RealmObject() {

    fun encodedName(): String = URLEncoder.encode(fileName, "UTF-8")

    fun createSubFolder(parent: String, createDatedSubFolders: Boolean) {
        remoteSubFolder = when {
            createDatedSubFolders -> {
                val date = fileModifiedAt.format("yyyy/MM/")
                "$parent/$date${encodedName()}"
            }
            else -> "$parent/${encodedName()}"
        }
    }

    fun store() {
        getRealmInstance().use {
            it.executeTransaction { realm ->
                realm.insertOrUpdate(this)
            }
        }
    }

    fun isSyncOffline() = type == Type.SYNC_OFFLINE.name

    fun refreshIdentifier() {
        getRealmInstance().use { realm ->
            syncFileByUriQuery(realm, uri).findFirst()?.apply {
                realm.executeTransaction { identifier = UUID.randomUUID().toString() }
            }
        }
    }

    fun getUriObject() = uri.toUri()

    fun getOriginalUri(context: Context): Uri {
        val uriObject = getUriObject()
        return when {
            DocumentsContract.isDocumentUri(context, uriObject) -> uriObject
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.setRequireOriginal(uriObject)
            else -> uriObject
        }
    }

    enum class Type {
        SYNC, UPLOAD, SHARED_FILE, SYNC_OFFLINE
    }

    companion object {
        private const val DB_NAME = "Sync.realm"
        private const val ONE_DAY = 24 * 60 * 60 * 1000
        private var realmConfiguration: RealmConfiguration = RealmConfiguration.Builder().name(DB_NAME)
            .schemaVersion(3) // Must be bumped when the schema changes
            .modules(RealmModules.SyncFilesModule())
            .migration(UploadMigration())
            .build()

        fun getRealmInstance(): Realm = Realm.getInstance(realmConfiguration)

        private fun syncFileByUriQuery(realm: Realm, uri: String): RealmQuery<UploadFile> {
            return realm.where(UploadFile::class.java).equalTo(UploadFile::uri.name, uri)
        }

        private fun pendingFilesQuery(realm: Realm, folderId: Int? = null): RealmQuery<UploadFile> {
            return realm.where(UploadFile::class.java).apply {
                folderId?.let { equalTo(UploadFile::remoteFolder.name, it) }
                equalTo(UploadFile::userId.name, AccountUtils.currentUserId)
                equalTo(UploadFile::driveId.name, AccountUtils.currentDriveId)
                isNull(UploadFile::uploadAt.name)
                isNull(UploadFile::deletedAt.name)
                sort(UploadFile::uri.name)
            }
        }

        fun getNotSyncFiles(): ArrayList<UploadFile> {
            return getRealmInstance().use { realm ->
                realm.where(UploadFile::class.java)
                    .isNull(UploadFile::uploadAt.name)
                    .isNull(UploadFile::deletedAt.name)
                    .findAll()?.map { realm.copyFromRealm(it, 0) } as? ArrayList<UploadFile> ?: arrayListOf()
            }
        }

        fun getNotSyncedFilesCount(): Long {
            return getRealmInstance().use { realm ->
                realm.where(UploadFile::class.java)
                    .isNull(UploadFile::uploadAt.name)
                    .isNull(UploadFile::deletedAt.name).count()
            }
        }

        fun getPendingFiles(folderId: Int): ArrayList<UploadFile> {
            return getRealmInstance().use { realm ->
                pendingFilesQuery(realm, folderId).findAll()
                    ?.map { realm.copyFromRealm(it, 0) } as? ArrayList<UploadFile> ?: arrayListOf()
            }
        }

        fun getUploadedFiles(type: String = Type.SYNC.name): ArrayList<UploadFile>? = getRealmInstance().use { realm ->
            realm.where(UploadFile::class.java)
                .equalTo(UploadFile::type.name, type)
                .isNull(UploadFile::deletedAt.name)
                .isNotNull(UploadFile::uploadAt.name)
                .findAll()?.map { realm.copyFromRealm(it, 0) } as? ArrayList<UploadFile>
        }

        fun getPendingFilesCount(folderId: Int? = null): Int {
            return getRealmInstance().use { realm ->
                pendingFilesQuery(realm, folderId).count().toInt()
            }
        }

        fun uploadFinished(uri: Uri) {
            getRealmInstance().use { realm ->
                syncFileByUriQuery(realm, uri.toString()).findFirst()?.let { syncFile ->
                    realm.executeTransaction {
                        syncFile.uploadAt = Date()
                    }
                }
            }
        }

        fun update(uri: String, transaction: (uploadFile: UploadFile) -> Unit): Boolean {
            getRealmInstance().use { realm ->
                return syncFileByUriQuery(realm, uri).findFirst()?.let { uploadFile ->
                    realm.executeTransaction { transaction(uploadFile) }
                    true
                } ?: false
            }
        }

        fun getLastDate(context: Context): Date {
            val date: Date? = getRealmInstance().use { realm ->
                realm.where(SyncSettings::class.java).findFirst()?.lastSync
            }

            return if (date == null) {
                Date(File(context.filesDir, DB_NAME).lastModified())
            } else {
                val now = System.currentTimeMillis()
                // If you change time zone
                if (date.time > now) Date(now - ONE_DAY) else date
            }
        }

        fun canUpload(uri: Uri, lastModified: Date): Boolean {
            return getRealmInstance().use { realm ->
                syncFileByUriQuery(realm, uri.toString())
                    .equalTo(UploadFile::fileModifiedAt.name, lastModified)
                    .findFirst() == null
            }
        }

        fun deleteIfExists(uri: Uri) {
            getRealmInstance().use { realm ->
                syncFileByUriQuery(realm, uri.toString()).findFirst()?.let { syncFile ->
                    realm.beginTransaction()
                    syncFile.deleteFromRealm()
                    realm.commitTransaction()
                }
            }
        }

        fun deleteAll(uploadFiles: ArrayList<UploadFile>) {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    uploadFiles.forEach { uploadFile ->
                        syncFileByUriQuery(realm, uploadFile.uri).findFirst()?.let { syncFile ->
                            syncFile.deletedAt = Date()
                            val uri = syncFile.getUriObject()
                            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                                if (!uploadFile.isSyncOffline()) uri.toFile().apply { if (exists()) delete() }
                            }
                        }
                    }
                }
            }
        }

        fun deleteAllByFolderId(folderID: Int) {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.where(UploadFile::class.java).equalTo(UploadFile::remoteFolder.name, folderID).findAll()
                        ?.deleteAllFromRealm()
                }
            }
        }

        fun deleteAllSyncFile() {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.where(UploadFile::class.java)
                        .equalTo(UploadFile::type.name, Type.SYNC.name)
                        .findAll()?.deleteAllFromRealm()
                }
            }
        }

        fun removeAppSyncSettings() {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.where(SyncSettings::class.java).findFirst()?.deleteFromRealm()
                }
            }
        }

        fun setAppSyncSettings(syncSettings: SyncSettings) {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.insertOrUpdate(syncSettings)
                }
            }
        }

        fun getAppSyncSettings(): SyncSettings? {
            return getRealmInstance().use { realm ->
                val realmObject = realm.where(SyncSettings::class.java).findFirst()
                realmObject?.let {
                    realm.copyFromRealm(it, 0)
                } ?: run { null }
            }
        }
    }
}