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
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration

object HandleSchemaVersionBelowZero {
    private val throwOldSchemaVersionRealmConfiguration = DriveInfosController.baseDriveInfosRealmConfigurationBuilder
        .migration(DetectTooOldSchemaMigration())
        .build()

    private val deleteTooOldSchemaVersionRealmConfiguration = DriveInfosController.baseDriveInfosRealmConfigurationBuilder
        .deleteRealmIfMigrationNeeded()
        .build()

    fun getRealmInstance(realmConfiguration: RealmConfiguration): Realm {
        var originalMigrationException: Throwable? = null

        return runCatching {
            Realm.getInstance(realmConfiguration)
        }.recoverCatching {
            originalMigrationException = it
            Realm.getInstance(throwOldSchemaVersionRealmConfiguration)
        }.getOrElse {
            if (it is OldSchemaVersion && it.oldVersion == 0L) {
                Realm.getInstance(deleteTooOldSchemaVersionRealmConfiguration)
            } else {
                throw requireNotNull(originalMigrationException) { "This field must have been set when the first runCatching has failed" }
            }
        }
    }

    private class DetectTooOldSchemaMigration : RealmMigration {
        override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) = throw OldSchemaVersion(oldVersion)
    }

    private class OldSchemaVersion(val oldVersion: Long) : Exception()
}
