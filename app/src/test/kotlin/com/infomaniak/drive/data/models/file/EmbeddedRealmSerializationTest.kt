/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.file

import com.infomaniak.core.network.api.ApiController
import com.infomaniak.drive.data.models.file.sharelink.ShareLinkCapabilities
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmbeddedRealmSerializationTest {

    @Test
    fun `decode embedded models with API field names`() {
        val externalImport = SerializationUtils.decodeFromString<FileExternalImport>(
            """
                {
                  "id": 42,
                  "application": "Google Drive",
                  "account_name": "account@example.com",
                  "status": "in_progress",
                  "path": "/folder",
                  "directory_id": 7,
                  "has_shared_files": "yes",
                  "created_at": 100,
                  "updated_at": 200,
                  "count_success_files": 3,
                  "count_failed_files": 1
                }
            """.trimIndent()
        )
        val capabilities = SerializationUtils.decodeFromString<ShareLinkCapabilities>(
            """{"can_edit":true,"can_see_stats":true,"can_download":false}"""
        )

        assertEquals(42, externalImport.id)
        assertEquals("Google Drive", externalImport.application)
        assertEquals("account@example.com", externalImport.accountName)
        assertEquals("in_progress", externalImport.status)
        assertEquals("/folder", externalImport.path)
        assertEquals(7, externalImport.directoryId)
        assertEquals("yes", externalImport.hasSharedFiles)
        assertEquals(100L, externalImport.createdAt)
        assertEquals(200L, externalImport.updatedAt)
        assertEquals(3, externalImport.countSuccessFiles)
        assertEquals(1, externalImport.countFailedFiles)
        assertEquals(true, capabilities.canEdit)
        assertEquals(true, capabilities.canSeeStats)
        assertEquals(false, capabilities.canDownload)
    }

    @Test
    fun `decode embedded models with absent null and unknown fields`() {
        val externalImport = SerializationUtils.decodeFromString<FileExternalImport>(
            """{"account_name":null,"unknown_field":"ignored"}"""
        )
        val capabilities = SerializationUtils.decodeFromString<ShareLinkCapabilities>(
            """{"can_edit":null,"unknown_field":"ignored"}"""
        )

        assertEquals(0, externalImport.id)
        assertEquals("", externalImport.application)
        assertEquals("", externalImport.accountName)
        assertEquals("", externalImport.status)
        assertEquals("", externalImport.path)
        assertEquals(0, externalImport.directoryId)
        assertEquals("", externalImport.hasSharedFiles)
        assertEquals(0L, externalImport.createdAt)
        assertEquals(0L, externalImport.updatedAt)
        assertEquals(0, externalImport.countSuccessFiles)
        assertEquals(0, externalImport.countFailedFiles)
        assertEquals(false, capabilities.canEdit)
        assertEquals(false, capabilities.canSeeStats)
        assertEquals(false, capabilities.canDownload)
    }

    @Test
    fun `Gson keeps API field mappings during gradual migration`() {
        val externalImport = ApiController.gson.fromJson(
            """{"account_name":"account@example.com","directory_id":7,"count_success_files":3}""",
            FileExternalImport::class.java
        )
        val capabilities = ApiController.gson.fromJson(
            """{"can_edit":true,"can_see_stats":true,"can_download":false}""",
            ShareLinkCapabilities::class.java
        )

        assertEquals("account@example.com", externalImport.accountName)
        assertEquals(7, externalImport.directoryId)
        assertEquals(3, externalImport.countSuccessFiles)
        assertEquals(true, capabilities.canEdit)
        assertEquals(true, capabilities.canSeeStats)
        assertEquals(false, capabilities.canDownload)
    }
}
