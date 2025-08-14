/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import io.realm.DynamicRealm
import io.realm.RealmMigration

class DriveMigration : RealmMigration {

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {

        // DynamicRealm exposes an editable schema
        val schema = realm.schema

        // Migrated to version 1
        if (oldVersion < 1L) {

            val driveQuotaSchema = schema.create("DriveQuota").apply {
                addField("current", Int::class.java)
                addField("max", Int::class.java)
                isEmbedded = true
            }

            val driveQuotasSchema = schema.create("DriveQuotas").apply {
                addRealmObjectField("dropbox", driveQuotaSchema)
                addRealmObjectField("sharedLink", driveQuotaSchema)
                isEmbedded = true
            }

            schema["Drive"]?.apply {
                if (hasField("_packFunctionality")) removeField("_packFunctionality")
                addRealmObjectField("_quotas", driveQuotasSchema)
            }
        }

        // Migrated to version 2
        if (oldVersion < 2L) {

            schema["Drive"]?.apply {
                renameField("accountAdmin", "isAdmin")
            }
        }
    }

    companion object {
        const val DB_VERSION = 2L // Must be bumped when the schema changes
    }
}
