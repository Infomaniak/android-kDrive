/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey

open class MediaFolder(
    @PrimaryKey var id: Long = 0L,
    var name: String = "",
    var isSynced: Boolean = false,
    var path: String = "",
) : RealmObject() {

    fun storeOrUpdate(newPath: String? = null) {
        return UploadFile.getRealmInstance().use {
            it.executeTransaction { realm ->
                findByIdQuery(realm, id)?.let { queryMedia ->
                    isSynced = queryMedia.isSynced
                    path = newPath ?: queryMedia.path
                }
                realm.insertOrUpdate(this)
            }
        }
    }

    fun enableSync(enable: Boolean) {
        getRealmInstance().use {
            findByIdQuery(it, id)?.let { mediaFolder ->
                it.executeTransaction {
                    mediaFolder.isSynced = enable
                }
            }
        }
    }

    companion object {

        private inline val Realm.mediaFolderTable get() = where(MediaFolder::class.java)

        fun getRealmInstance() = UploadFile.getRealmInstance()

        private fun findByIdQuery(realm: Realm, id: Long) = realm.mediaFolderTable.equalTo(MediaFolder::id.name, id).findFirst()

        fun findById(realm: Realm, id: Long): MediaFolder? {
            return findByIdQuery(realm, id)?.let { mediaFolder ->
                realm.copyFromRealm(mediaFolder, 0)
            }
        }

        fun getAll(realm: Realm): ArrayList<MediaFolder> {
            return realm.mediaFolderTable.sort(MediaFolder::name.name, Sort.ASCENDING).findAll()?.let { results ->
                ArrayList(realm.copyFromRealm(results, 0))
            } ?: arrayListOf()
        }

        fun getAllSyncedFolders(): List<MediaFolder> {
            return getRealmInstance().use {
                it.mediaFolderTable.equalTo(MediaFolder::isSynced.name, true).findAll().freeze() ?: emptyList()
            }
        }

        fun getAllSyncedFoldersCount(): Long {
            return getRealmInstance().use { realm ->
                realm.mediaFolderTable.equalTo(MediaFolder::isSynced.name, true).count()
            }
        }

        fun getAllCount(realm: Realm): Long {
            return realm.mediaFolderTable.count()
        }

        fun delete(realm: Realm, mediaFolderId: Long) {
            realm.mediaFolderTable.equalTo(MediaFolder::id.name, mediaFolderId).findFirst()?.deleteFromRealm()
        }

        fun deleteAll() {
            return getRealmInstance().use {
                it.executeTransaction { realm ->
                    realm.delete(MediaFolder::class.java)
                }
            }
        }
    }
}
