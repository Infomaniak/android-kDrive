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
package com.infomaniak.drive.data.sync

import com.infomaniak.drive.data.models.UploadFile
import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

@Suppress("UNUSED_CHANGED_VALUE")
class UploadMigration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var oldVersionTemp = oldVersion

        // DynamicRealm exposes an editable schema
        val schema = realm.schema

        // Migrate to version 1: Add original local uri for sync files
        if (oldVersionTemp == 0L) {
            schema.get(UploadFile::class.java.simpleName)!!
                .addField(UploadFile::originalLocalUri.name, String::class.java, FieldAttribute.REQUIRED)
            oldVersionTemp++
        }
    }
}