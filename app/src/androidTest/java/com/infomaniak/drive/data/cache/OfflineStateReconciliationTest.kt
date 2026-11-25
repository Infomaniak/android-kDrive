/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.data.cache

import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.RealmModules
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OfflineStateReconciliationTest {

    @Test
    fun refreshesDetachedFilesByUidWithoutModifyingManagedFiles() {
        val config = RealmConfiguration.Builder()
            .name("offline-test-${UUID.randomUUID()}.realm")
            .inMemory()
            .modules(RealmModules.LocalFilesModule())
            .build()

        Realm.getInstance(config).use { realm ->
            realm.executeTransaction {
                it.copyToRealm(File(uid = "1_10", id = 1, driveId = 10, isOffline = true, isMarkedAsOffline = true))
                it.copyToRealm(File(uid = "1_20", id = 1, driveId = 20, isOffline = false))
            }

            val managed = realm.where(File::class.java).equalTo(File::uid.name, "1_10").findFirst()!!
            val first = File(id = 1, driveId = 10)
            val duplicate = File(id = 1, driveId = 10)
            val otherDrive = File(id = 1, driveId = 20, isOffline = true)
            val missing = File(id = 1, driveId = 30)

            FileController.refreshOfflineStates(realm, listOf(managed, first, duplicate, otherDrive, missing))

            assertTrue(first.isOffline)
            assertTrue(first.isMarkedAsOffline)
            assertTrue(duplicate.isOffline)
            assertFalse(otherDrive.isOffline)
            assertFalse(missing.isOffline)
            assertTrue(managed.isOffline)
        }
    }
}
