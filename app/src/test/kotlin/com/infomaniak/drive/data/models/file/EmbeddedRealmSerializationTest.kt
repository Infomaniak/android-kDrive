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
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.data.models.TeamDetails
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.data.models.drive.DriveAccount
import com.infomaniak.drive.data.models.drive.DriveCapabilities
import com.infomaniak.drive.data.models.drive.DrivePack
import com.infomaniak.drive.data.models.drive.DrivePackCapabilities
import com.infomaniak.drive.data.models.drive.DrivePreferences
import com.infomaniak.drive.data.models.drive.DriveQuota
import com.infomaniak.drive.data.models.drive.DriveQuotas
import com.infomaniak.drive.data.models.drive.DriveRights
import com.infomaniak.drive.data.models.drive.MaintenanceType
import com.infomaniak.drive.data.models.file.FileVersion
import com.infomaniak.drive.data.models.file.sharelink.ShareLinkCapabilities
import com.infomaniak.drive.data.models.file.dropbox.DropBoxSize
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmbeddedRealmSerializationTest {

    @Test
    fun `decode remaining scalar Realm models with API field names`() {
        val rights = SerializationUtils.decodeFromString<Rights>(
            """{"can_become_dropbox":true,"can_create_directory":true,"can_write":true}"""
        )
        val categoryRights = SerializationUtils.decodeFromString<CategoryRights>(
            """{"can_create":true,"can_put_on_file":true,"can_read_on_file":true}"""
        )
        val driveCapabilities = SerializationUtils.decodeFromString<DriveCapabilities>(
            """{"use_versioning":true,"use_upload_compression":true,"can_upgrade_to_ksuite":true}"""
        )
        val packCapabilities = SerializationUtils.decodeFromString<DrivePackCapabilities>(
            """{"use_vault":true,"can_set_sharelink_expiration":true,"can_set_sharelink_custom_url":true}"""
        )
        val fileVersion = SerializationUtils.decodeFromString<FileVersion>(
            """{"is_multiple":true,"number":4,"total_size":2048}"""
        )
        val driveQuotas = SerializationUtils.decodeFromString<DriveQuotas>(
            """{"dropbox":{"current":1,"max":2},"shared_link":{"current":3,"max":4}}"""
        )
        val driveQuota = SerializationUtils.decodeFromString<DriveQuota>("""{"current":5,"max":6}""")
        val dropboxSize = SerializationUtils.decodeFromString<DropBoxSize>(
            """{"limit":4096,"remaining":1024}"""
        )
        val teamDetails = SerializationUtils.decodeFromString<TeamDetails>(
            """{"drive_id":42,"users_count":7}"""
        )
        val driveRights = SerializationUtils.decodeFromString<DriveRights>("""{"technical":true}""")

        assertEquals(true, rights.canBecomeDropbox)
        assertEquals(true, rights.canCreateDirectory)
        assertEquals(true, rights.canWrite)
        assertEquals(true, categoryRights.canCreate)
        assertEquals(true, categoryRights.canPutOnFile)
        assertEquals(true, categoryRights.canReadOnFile)
        assertEquals(true, driveCapabilities.useVersioning)
        assertEquals(true, driveCapabilities.useUploadCompression)
        assertEquals(true, driveCapabilities.canUpgradeToKsuite)
        assertEquals(true, packCapabilities.useVault)
        assertEquals(true, packCapabilities.canSetSharelinkExpiration)
        assertEquals(true, packCapabilities.canSetSharelinkCustomUrl)
        assertEquals(true, fileVersion.isMultiple)
        assertEquals(4, fileVersion.number)
        assertEquals(2048L, fileVersion.totalSize)
        assertEquals(1, driveQuotas.dropbox?.current)
        assertEquals(2, driveQuotas.dropbox?.max)
        assertEquals(3, driveQuotas.sharedLink?.current)
        assertEquals(4, driveQuotas.sharedLink?.max)
        assertEquals(5, driveQuota.current)
        assertEquals(6, driveQuota.max)
        assertEquals(4096L, dropboxSize.limit)
        assertEquals(1024L, dropboxSize.remaining)
        assertEquals(42, teamDetails.driveId)
        assertEquals(7, teamDetails.usersCount)
        assertEquals(true, driveRights.technical)
    }

    @Test
    fun `decode and encode Realm models with private API backing fields`() {
        val account = SerializationUtils.decodeFromString<DriveAccount>(
            """{"id":12,"name":"Example","legal_entity_type":"company"}"""
        )
        val preferences = SerializationUtils.decodeFromString<DrivePreferences>(
            """{"color":"#123456","hide":true}"""
        )
        val pack = SerializationUtils.decodeFromString<DrivePack>(
            """{"id":3,"name":"KSUITE_PRO","capabilities":{"use_dropbox":true}}"""
        )
        val user = SerializationUtils.decodeFromString<DriveUser>(
            """{"id":5,"display_name":"User","role":"external"}"""
        )

        assertEquals(DriveAccount.LegalEntityType.COMPANY, account.legalEntityType)
        assertEquals("#123456", preferences.color)
        assertEquals(true, preferences.hide)
        assertEquals(true, pack.capabilities.useDropbox)
        assertEquals(DriveUser.Role.EXTERNAL, user.role)
        assertEquals(
            """{"id":12,"name":"Example","legal_entity_type":"company"}""",
            SerializationUtils.encodeToString(account)
        )
        assertEquals(
            """{"color":"#123456","hide":true}""",
            SerializationUtils.encodeToString(preferences)
        )
        assertEquals(
            """{"id":3,"name":"KSUITE_PRO","capabilities":{"use_vault":false,"use_manage_right":false,"can_set_trash_duration":false,"use_dropbox":true,"can_rewind":false,"use_folder_custom_color":false,"can_access_dashboard":false,"can_set_sharelink_password":false,"can_set_sharelink_expiration":false,"can_set_sharelink_custom_url":false}}""",
            SerializationUtils.encodeToString(pack)
        )
    }

    @Test
    fun `decode and encode DriveUser role API enum values`() {
        assertEquals(
            DriveUser.Role.ADMIN,
            SerializationUtils.decodeFromString<DriveUser.Role>(""" "admin" """)
        )
        assertEquals(
            """"external"""",
            SerializationUtils.encodeToString(DriveUser.Role.EXTERNAL)
        )
    }

    @Test
    fun `decode and encode maintenance type API code`() {
        val maintenanceType = SerializationUtils.decodeFromString<MaintenanceType>(
            """{"code":"move_ns"}"""
        )

        assertEquals(MaintenanceType.MaintenanceTypeValue.MoveNs, maintenanceType.type)
        assertEquals("""{"code":"move_ns"}""", SerializationUtils.encodeToString(maintenanceType))
    }

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

}
