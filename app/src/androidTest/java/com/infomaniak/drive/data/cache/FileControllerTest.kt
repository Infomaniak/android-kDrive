/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.drive.KDriveTest
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileControllerTest : KDriveTest() {

    private val firstSharedDriveId = 1001
    private val secondSharedDriveId = 1002
    private val duplicatedFileId = 42

    private fun sharedWithMeUserDrive(driveId: Int = 0): UserDrive {
        return UserDrive(userId = user.id, driveId = driveId, sharedWithMe = true)
    }

    @BeforeEach
    fun clearRealms() {
        FileController.getRealmInstance(userDrive).use { realm ->
            realm.executeTransaction { it.deleteAll() }
        }
        FileController.getRealmInstance(sharedWithMeUserDrive()).use { realm ->
            realm.executeTransaction { it.deleteAll() }
        }
    }

    @Test
    fun `getFileByUidOrId returns the matching shared file and keeps id fallback`() {
        val defaultFile = File(id = duplicatedFileId, driveId = userDrive.driveId, name = "default").apply { initUid() }
        val firstSharedFile = File(id = duplicatedFileId, driveId = firstSharedDriveId, name = "shared-first").apply { initUid() }
        val secondSharedFile = File(id = duplicatedFileId, driveId = secondSharedDriveId, name = "shared-second").apply { initUid() }

        FileController.getRealmInstance(userDrive).use { realm ->
            realm.executeTransaction { it.insertOrUpdate(defaultFile) }
        }
        FileController.getRealmInstance(sharedWithMeUserDrive()).use { realm ->
            realm.executeTransaction {
                it.insertOrUpdate(firstSharedFile)
                it.insertOrUpdate(secondSharedFile)
            }
        }

        val firstResult = FileController.getFileByUidOrId(
            fileId = duplicatedFileId,
            userDrive = sharedWithMeUserDrive(driveId = firstSharedDriveId),
        )
        val secondResult = FileController.getFileByUidOrId(
            fileId = duplicatedFileId,
            userDrive = sharedWithMeUserDrive(driveId = secondSharedDriveId),
        )
        val fallbackResult = FileController.getFileByUidOrId(fileId = duplicatedFileId)

        assertNotNull(firstResult)
        assertEquals(firstSharedDriveId, firstResult?.driveId)
        assertEquals(firstSharedFile.uid, firstResult?.uid)

        assertNotNull(secondResult)
        assertEquals(secondSharedDriveId, secondResult?.driveId)
        assertEquals(secondSharedFile.uid, secondResult?.uid)

        assertNotNull(fallbackResult)
        assertEquals(userDrive.driveId, fallbackResult?.driveId)
        assertEquals(defaultFile.uid, fallbackResult?.uid)
    }
}
