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
package com.infomaniak.drive.data.cache

import com.infomaniak.drive.data.models.File
import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

class FileMigration : RealmMigration {
    companion object {
        const val bddVersion = 1L // Must be bumped when the schema changes
    }

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var oldVersionTemp = oldVersion

        // DynamicRealm exposes an editable schema
        val schema = realm.schema

        // Migrate to version 1
        if (oldVersionTemp == 0L) {
            schema.get(File::class.java.simpleName)?.apply {
                removeField("order")
                removeField("orderBy")
                if (hasField("canUseTag")) removeField("canUseTag")
                if (hasField("tags")) removeField("tags")
                if (hasField("isWaitingOffline")) removeField("isWaitingOffline")
                if (!hasField(File::isFromSearch.name)) {
                    addField(File::isFromSearch.name, Boolean::class.java, FieldAttribute.REQUIRED)
                }
                if (!hasField(File::isFromUploads.name)) {
                    addField(File::isFromUploads.name, Boolean::class.java, FieldAttribute.REQUIRED)
                }
            }
            oldVersionTemp++
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }


}