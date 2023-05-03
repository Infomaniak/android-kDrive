/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.sync.UploadMigration
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.utils.format
import io.realm.*
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import io.realm.kotlin.oneOf
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.Date

open class UploadFile(
    @PrimaryKey var uri: String = "",
    var deletedAt: Date? = null,
    var driveId: Int = -1,
    var fileCreatedAt: Date? = null,
    var fileModifiedAt: Date = Date(),
    var fileName: String = "",
    var fileSize: Long = 0L,
    var uploadToken: String? = null,
    var remoteFolder: Int = -1,
    var remoteSubFolder: String? = null,
    var type: String = Type.SYNC.name,
    var uploadAt: Date? = null,
    var userId: Int = -1,
) : RealmObject() {

    @delegate:Ignore
    val okHttpClient: OkHttpClient by lazy { runBlocking { AccountUtils.getHttpClient(userId = userId, timeout = 120) } }

    fun createSubFolder(parent: String, createDatedSubFolders: Boolean) {
        remoteSubFolder = parent + if (createDatedSubFolders) "/${fileModifiedAt.format("yyyy/MM")}" else ""
    }

    fun store() {
        getRealmInstance().use {
            it.executeTransaction { realm ->
                realm.insertOrUpdate(this)
            }
        }
    }

    fun isSync() = type == Type.SYNC.name

    fun isSyncOffline() = type == Type.SYNC_OFFLINE.name

    private fun isCloudStorage() = type == Type.CLOUD_STORAGE.name

    fun replaceOnConflict() = isSync() || isSyncOffline() || isCloudStorage()

    fun resetUploadToken() {
        getRealmInstance().use { realm ->
            syncFileByUriQuery(realm, uri).findFirst()?.apply {
                realm.executeTransaction { uploadToken = null }
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

    fun updateFileSize(newFileSize: Long) {
        getRealmInstance().use { realm ->
            syncFileByUriQuery(realm, uri).findFirst()?.let { uploadFile ->
                realm.executeTransaction { uploadFile.fileSize = newFileSize }
            }
        }
        fileSize = newFileSize
    }

    fun updateUploadToken(newUploadToken: String) {
        getRealmInstance().use { realm ->
            syncFileByUriQuery(realm, uri).findFirst()?.let { uploadFile ->
                realm.executeTransaction { uploadFile.uploadToken = newUploadToken }
            }
        }
        uploadToken = newUploadToken
    }

    fun deleteIfExists(keepFile: Boolean = false) {
        getRealmInstance().use { realm ->
            syncFileByUriQuery(realm, uri).findFirst()?.let { uploadFileProxy ->
                // Cancel session if exists
                uploadFileProxy.uploadToken?.let {
                    with(ApiRepository.cancelSession(uploadFileProxy.driveId, it, okHttpClient)) {
                        if (translatedError == R.string.connectionError) throw UploadTask.NetworkException()
                    }
                }
                // Delete in realm
                realm.executeTransaction {
                    if (uploadFileProxy.isValid) {
                        if (keepFile) uploadFileProxy.deletedAt = Date() else uploadFileProxy.deleteFromRealm()
                    }
                }
            }
        }
    }

    enum class Type {
        SYNC, UPLOAD, SHARED_FILE, SYNC_OFFLINE, CLOUD_STORAGE
    }

    companion object {
        private const val DB_NAME = "Sync.realm"
        private const val ONE_DAY = 24 * 60 * 60 * 1000
        private var realmConfiguration: RealmConfiguration = RealmConfiguration.Builder().name(DB_NAME)
            .schemaVersion(UploadMigration.dbVersion)
            .modules(RealmModules.SyncFilesModule())
            .migration(UploadMigration())
            .build()

        private inline val Realm.uploadTable get() = where(UploadFile::class.java)

        fun getRealmInstance(): Realm {
            return runCatching {
                Realm.getInstance(realmConfiguration)
            }.getOrElse { exception ->
                // Temporary fix, Issue https://github.com/realm/realm-java/issues/7706
                Sentry.captureException(exception)
                // Delete the database because it could be corrupted
                Realm.deleteRealm(realmConfiguration)
                // Create a new database
                Realm.getInstance(realmConfiguration)
            }
        }

        private fun syncFileByUriQuery(realm: Realm, uri: String): RealmQuery<UploadFile> {
            return realm.uploadTable.equalTo(UploadFile::uri.name, uri)
        }

        private fun pendingUploadsQuery(
            realm: Realm,
            folderId: Int? = null,
            onlyCurrentUser: Boolean = false,
            driveIds: Array<Int>? = null
        ): RealmQuery<UploadFile> {
            return realm.uploadTable.apply {
                folderId?.let { equalTo(UploadFile::remoteFolder.name, it) }
                if (onlyCurrentUser) equalTo(UploadFile::userId.name, AccountUtils.currentUserId)
                driveIds?.let { oneOf(UploadFile::driveId.name, it) }
                isNull(UploadFile::uploadAt.name)
                isNull(UploadFile::deletedAt.name)
            }
        }

        private fun allPendingFoldersQuery(realm: Realm): RealmQuery<UploadFile> {
            return pendingUploadsQuery(realm, onlyCurrentUser = true, driveIds = currentDriveAndSharedWithMeIds())
                .distinct(UploadFile::remoteFolder.name)
        }

        private fun currentDriveAndSharedWithMeIds(): Array<Int> {
            val sharedWithMeIds = DriveInfosController.getDrives(AccountUtils.currentUserId, sharedWithMe = true).map { it.id }
            return arrayOf(AccountUtils.currentDriveId, *sharedWithMeIds.toTypedArray())
        }

        fun getAllPendingUploads(customRealm: Realm? = null): ArrayList<UploadFile> {
            val block: (Realm) -> ArrayList<UploadFile> = { realm ->
                val results = pendingUploadsQuery(realm).findAll()
                val priorityUploadFiles = results.where().notEqualTo(UploadFile::type.name, Type.SYNC.name).findAll()
                val syncUploadFiles = results.where().equalTo(UploadFile::type.name, Type.SYNC.name).findAll()
                arrayListOf(
                    *realm.copyFromRealm(priorityUploadFiles, 0).toTypedArray(),
                    *realm.copyFromRealm(syncUploadFiles, 0).toTypedArray()
                )
            }

            return customRealm?.let(block) ?: getRealmInstance().use(block)
        }

        fun getAllPendingPriorityFilesCount(): Long {
            return getRealmInstance().use { pendingUploadsQuery(it).notEqualTo(UploadFile::type.name, Type.SYNC.name).count() }
        }

        fun getAllPendingFolders(realm: Realm): RealmResults<UploadFile>? {
            return allPendingFoldersQuery(realm).findAll()
        }

        fun getAllPendingFoldersCount(realm: Realm): Long {
            return allPendingFoldersQuery(realm).count()
        }

        fun getCurrentUserPendingUploads(realm: Realm, folderId: Int): RealmResults<UploadFile>? {
            return pendingUploadsQuery(realm, folderId, true).findAll()
        }

        fun getAllPendingUploadsCount(): Int {
            return getRealmInstance().use { realm ->
                pendingUploadsQuery(realm).count().toInt()
            }
        }

        fun getCurrentUserPendingUploadsCount(folderId: Int? = null): Int {
            return getRealmInstance().use { realm ->
                pendingUploadsQuery(realm, folderId, true, driveIds = currentDriveAndSharedWithMeIds()).count().toInt()
            }
        }

        fun getAllUploadedFiles(type: String = Type.SYNC.name): ArrayList<UploadFile>? = getRealmInstance().use { realm ->
            realm.uploadTable
                .equalTo(UploadFile::type.name, type)
                .isNull(UploadFile::deletedAt.name)
                .isNotNull(UploadFile::uploadAt.name)
                .findAll()?.map { realm.copyFromRealm(it, 0) } as? ArrayList<UploadFile>
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

        fun deleteAll(uploadFiles: List<UploadFile>) {
            getRealmInstance().use {
                it.executeTransaction { realm ->
                    uploadFiles.forEach { uploadFile ->
                        syncFileByUriQuery(realm, uploadFile.uri).findFirst()?.let { uploadFileRealm ->
                            // Don't delete definitively if it's a sync
                            if (uploadFileRealm.type == Type.SYNC.name) {
                                uploadFileRealm.deletedAt = Date()
                            } else {
                                // Delete definitively
                                val uri = uploadFileRealm.getUriObject()
                                if (uri.scheme.equals(ContentResolver.SCHEME_FILE) && !uploadFile.isSyncOffline()) {
                                    uri.toFile().apply { if (exists()) delete() }
                                }
                                uploadFileRealm.deleteFromRealm()
                            }
                        }
                    }
                }
            }
        }

        fun cancelAllPendingFilesSessions(folderId: Int) {
            getRealmInstance().use { realm ->
                realm.uploadTable
                    .equalTo(UploadFile::remoteFolder.name, folderId)
                    .isNull(UploadFile::uploadAt.name)
                    .isNotNull(UploadFile::uploadToken.name)
                    .findAll()?.onEach { uploadFileProxy ->
                        with(uploadFileProxy) {
                            ApiRepository.cancelSession(driveId, uploadToken!!, okHttpClient)
                        }
                    }
            }
        }

        fun deleteAll(folderId: Int?, permanently: Boolean = false) {
            getRealmInstance().use {
                it.executeTransaction { realm ->
                    // Delete all data files for all uploads with scheme FILE
                    realm.uploadTable
                        .apply { folderId?.let { equalTo(UploadFile::remoteFolder.name, folderId) } }
                        .beginsWith(UploadFile::uri.name, ContentResolver.SCHEME_FILE)
                        .findAll().forEach { uploadFile ->
                            if (!uploadFile.isSyncOffline()) uploadFile.getUriObject().toFile().apply { if (exists()) delete() }
                        }

                    if (permanently) {
                        realm.uploadTable
                            .apply { folderId?.let { equalTo(UploadFile::remoteFolder.name, folderId) } }
                            .isNull(UploadFile::uploadAt.name)
                            .findAll().deleteAllFromRealm()
                    } else {
                        // Delete all uploads with type SYNC
                        pendingUploadsQuery(realm, folderId)
                            .equalTo(UploadFile::type.name, Type.SYNC.name)
                            .isNull(UploadFile::uploadAt.name)
                            .findAll().forEach { uploadFile -> uploadFile.deletedAt = Date() }

                        // Delete all uploads without type SYNC
                        realm.uploadTable
                            .apply { folderId?.let { equalTo(UploadFile::remoteFolder.name, folderId) } }
                            .notEqualTo(UploadFile::type.name, Type.SYNC.name)
                            .findAll().deleteAllFromRealm()
                    }
                }
            }
        }

        fun deleteAllSyncFile(realm: Realm? = null) {
            val block: (Realm) -> Unit = { realm ->
                val transaction: (Realm) -> Unit = {
                    it.uploadTable
                        .equalTo(UploadFile::type.name, Type.SYNC.name)
                        .findAll()?.deleteAllFromRealm()
                }
                if (realm.isInTransaction) transaction(realm) else realm.executeTransaction(transaction)
            }
            realm?.let(block) ?: getRealmInstance().use(block)
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
                realm.executeTransaction { it.insertOrUpdate(syncSettings) }
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
