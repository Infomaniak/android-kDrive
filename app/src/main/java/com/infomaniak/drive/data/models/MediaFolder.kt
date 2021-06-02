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

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class MediaFolder(
    @PrimaryKey var id: Long = 0L,
    var name: String = "",
    var isSynced: Boolean = false
) : RealmObject() {

    fun storeOrUpdate() {
        return UploadFile.getRealmInstance().use {
            it.executeTransaction { realm ->
                findByIdQuery(realm, id)?.let { queryMedia ->
                    isSynced = queryMedia.isSynced
                }
                realm.insertOrUpdate(this)
            }
        }
    }

    fun delete() {
        UploadFile.getRealmInstance().use {
            it.executeTransaction { realm ->
                realm.where(MediaFolder::class.java).equalTo(MediaFolder::id.name, id).findFirst()?.deleteFromRealm()
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

        private fun getRealmInstance() = UploadFile.getRealmInstance()

        private fun findByIdQuery(realm: Realm, id: Long) =
            realm.where(MediaFolder::class.java).equalTo(MediaFolder::id.name, id).findFirst()

        fun findById(id: Long): MediaFolder? {
            return getRealmInstance().use { realm ->
                findByIdQuery(realm, id)?.let { mediaFolder ->
                    realm.copyFromRealm(mediaFolder, 0)
                }
            }
        }

        fun getAll(): ArrayList<MediaFolder> {
            return getRealmInstance().use { realm ->
                realm.where(MediaFolder::class.java).findAll()?.let { results ->
                    ArrayList(realm.copyFromRealm(results, 0))
                } ?: arrayListOf()
            }
        }

        fun getAllSyncedFolders(): ArrayList<MediaFolder> {
            return getRealmInstance().use { realm ->
                realm.where(MediaFolder::class.java).equalTo(MediaFolder::isSynced.name, true).findAll()?.let { results ->
                    ArrayList(realm.copyFromRealm(results, 0))
                } ?: arrayListOf()
            }
        }

        fun getAllSyncedFoldersCount(): Long {
            return getRealmInstance().use { realm ->
                realm.where(MediaFolder::class.java).equalTo(MediaFolder::isSynced.name, true).count()
            }
        }
    }
}