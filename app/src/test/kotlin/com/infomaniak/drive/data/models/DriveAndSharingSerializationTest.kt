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
package com.infomaniak.drive.data.models

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.data.models.drive.DrivePack.DrivePackType
import com.infomaniak.drive.data.models.drive.MaintenanceType.MaintenanceTypeValue
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class DriveAndSharingSerializationTest {

    private inline fun <reified T> decode(json: String) = SerializationUtils.decodeFromString<ApiResponse<T>>(json)

    private val driveJson = """
        {"id":12,"name":"My drive","account_admin":true,"role":"admin","used_size":10,"size":100,
         "created_at":1700000000,"updated_at":1700000100,"in_maintenance":true,"maintenance_reason":"technical",
         "maintenance_types":[{"code":"asleep"},{"code":"something_new"}],
         "rights":{"technical":true},"preferences":{"color":"#112233","hide":true},
         "capabilities":{"use_versioning":true,"can_rewind":true},
         "quota":{"dropbox":{"current":1,"max":2},"shared_link":null},
         "categories_permissions":{"can_create":true},
         "account":{"id":5,"name":"Acme","legal_entity_type":"company"},
         "pack":{"id":1,"name":"kSuite_Pro","capabilities":{"use_dropbox":true}},
         "users":{"account":[1,2],"drive":[3],"internal":[4,5],"external":[]},
         "teams":{"account":[7],"drive":[]},
         "categories":[{"id":1,"name":"Blue","color":"#00F","created_at":1700000000,"is_predefined":false}],
         "unknown_field":"ignored"}
    """.trimIndent()

    @Test
    fun `decodes drive info with the whole drive graph`() {
        val info = decode<DriveInfo>(
            """{"result":"success","data":{"drives":[$driveJson],"users":[{"id":1,"display_name":"Ada Lovelace","role":"user"}],
              "teams":[{"id":2,"name":"Devs","color":3,"users":[1,2],"details":[{"drive_id":12,"users_count":2}]}],
              "ips":{"uuid":"abc","valid_until":1700000500}}}"""
        ).data!!

        val drive = info.drives.single()
        assertEquals(12, drive.id)
        assertTrue(drive.isOrganisationAdmin)
        assertEquals("My drive", drive.name)
        assertEquals(DriveUser.Role.ADMIN, drive.role)
        assertEquals(1_700_000_000L, drive.createdAt)
        assertTrue(drive.isTechnicalMaintenance)
        assertEquals(listOf(MaintenanceTypeValue.Asleep, null), drive.maintenanceTypes.map { it.type })
        assertTrue(drive.rights.technical)
        assertEquals("#112233", drive.preferences.color)
        assertTrue(drive.preferences.hide)
        assertTrue(drive.capabilities.useVersioning)
        assertFalse(drive.capabilities.useUploadCompression)
        assertEquals(1, drive.quotas.dropbox?.current)
        assertNull(drive.quotas.sharedLink)
        assertTrue(drive.categoryRights.canCreate)
        assertEquals("Acme", drive.driveAccount.name)
        assertEquals(DrivePackType.KSUITE_PRO, drive.pack?.type)
        assertTrue(drive.pack!!.capabilities.useDropbox)
        assertEquals(listOf(4, 5), drive.users.internal.toList())
        assertEquals(listOf(7), drive.teams.account.toList())
        assertEquals("Blue", drive.categories.single().name)
        assertEquals(Date(1_700_000_000_000L), drive.categories.single().createdAt)

        assertEquals("Ada Lovelace", info.users.single().displayName)
        assertEquals(listOf(1, 2), info.teams.single().users.toList())
        assertEquals(12, info.teams.single().details.single().driveId)
        assertEquals("abc", info.ipsToken.uuid)
        assertEquals(Date(1_700_000_500_000L), info.ipsToken.validUntil)
    }

    @Test
    fun `drive falls back to defaults on absent or null fields and unknown values`() {
        val drive = SerializationUtils.decodeFromString<com.infomaniak.drive.data.models.drive.Drive>(
            """{"id":3,"role":"galactic_emperor","rights":null,"quota":null,"maintenance_types":null,"users":null,"pack":null}"""
        )

        assertEquals(3, drive.id)
        assertNull(drive.role)
        assertFalse(drive.rights.technical)
        assertNull(drive.quotas.dropbox)
        assertTrue(drive.maintenanceTypes.isEmpty())
        assertTrue(drive.users.internal.isEmpty())
        assertEquals("", drive.name)
    }

    @Test
    fun `decodes file share with users teams and invitations`() {
        val share = decode<Share>(
            """{"result":"success","data":{
              "teams":[{"id":0,"name":"All users","right":"read","status":"active"}],
              "users":[{"id":9,"name":"Bob","right":"write","status":"pending","role":"external","email":"b@x.ch",
                        "user":{"id":9,"display_name":"Bob","role":"external"}},
                       {"id":10,"name":"Eve","right":"manage","status":"brand_new","role":"owner"}],
              "invitations":[{"id":4,"email":"i@x.ch","right":"read","invitation_drive_id":12,"status":"pending"}]}}"""
        ).data!!

        assertTrue(share.teams.single().isAllUsers())
        val (bob, eve) = share.users
        assertEquals(UserFileAccess.UserFileAccessStatus.PENDING, bob.status)
        assertEquals(DriveUser.Role.EXTERNAL, bob.role)
        assertTrue(bob.isExternalUser)
        assertEquals("Bob", bob.user?.displayName)
        assertEquals(UserFileAccess.UserFileAccessStatus.ACTIVE, eve.status)
        assertEquals(DriveUser.Role.NONE, eve.role)
        assertEquals(12, share.invitations.single().invitationDriveId)
        assertEquals(4, share.members.size)
        assertTrue(share.driveUsers.isEmpty())
    }

    @Test
    fun `decodes multi access feedback`() {
        val items = decode<ShareableItems>(
            """{"result":"success","data":{
              "emails":[{"id":"a@b.ch","result":true,"access":{"id":1,"email":"a@b.ch","invitation_drive_id":3}},
                        {"id":"c@d.ch","result":false,"message":"no"}],
              "teams":[{"id":2,"result":true,"access":{"id":2,"name":"T"}}],
              "users":[{"id":5,"result":true,"access":null}]}}"""
        ).data!!

        assertEquals(3, items.emails.first().access?.invitationDriveId)
        assertNull(items.emails[1].access)
        assertEquals("no", items.emails[1].message)
        assertEquals("T", items.teams.single().access?.name)
        assertNull(items.users.single().access)
    }

    @Test
    fun `decodes category feedback lists with Unit access`() {
        val single = decode<ShareableItems.FeedbackAccessResource<Int, Unit>>(
            """{"result":"success","data":{"id":4,"result":true}}"""
        ).data!!
        val several = decode<List<ShareableItems.FeedbackAccessResource<Int, Unit>>>(
            """{"result":"success","data":[{"id":4,"result":true,"access":null,"message":""},{"id":5,"result":false,"message":"nope"}]}"""
        ).data!!

        assertTrue(single.result)
        assertEquals(listOf(4, 5), several.map { it.id })
        assertEquals(listOf(true, false), several.map { it.result })
        assertEquals("nope", several[1].message)
    }

    @Test
    fun `decodes cursor responses of comments with pagination fields`() {
        val comment = """{"id":1,"user":{"id":2,"display_name":"Ada"},"body":"hi","liked":false,"parent_id":0,
            "updated_at":1700000000,"created_at":1700000000,"likes_count":0,"is_resolved":false,"responses_count":0}"""
        val response = SerializationUtils.decodeFromString<CursorApiResponse<ArrayList<FileComment>>>(
            """{"result":"success","data":[$comment],"response_at":1700000001,"cursor":"abc","has_more":true,
               "total":1,"page":2,"pages":3,"items_per_page":10,"uri":"/x"}"""
        )

        assertEquals(ApiResponseStatus.SUCCESS, response.result)
        assertEquals("hi", response.data!!.single().body)
        assertEquals(1_700_000_001L, response.responseAt)
        assertEquals("abc", response.cursor)
        assertTrue(response.hasMore)
        assertTrue(response.hasMoreAndCursorExists)
        assertEquals(1, response.total)
        assertEquals(2, response.page)
        assertEquals(3, response.pages)
        assertEquals(10, response.itemsPerPage)
        assertEquals("/x", response.uri)
    }

    @Test
    fun `cursor responses tolerate absent null and unknown fields`() {
        val response = SerializationUtils.decodeFromString<CursorApiResponse<List<String>>>(
            """{"result":"whatever","data":null,"cursor":null,"has_more":null,"extra":1}"""
        )
        assertEquals(ApiResponseStatus.UNKNOWN, response.result)
        assertNull(response.data)
        assertNull(response.cursor)
        assertFalse(response.hasMore)
        assertFalse(response.hasMoreAndCursorExists)

        val last = SerializationUtils.decodeFromString<CursorApiResponse<List<String>>>(
            """{"result":"success","data":["a"],"has_more":true}"""
        )
        assertTrue(last.hasMore)
        assertFalse(last.hasMoreAndCursorExists)
    }

    @Test
    fun `decodes cursor error responses`() {
        val response = SerializationUtils.decodeFromString<CursorApiResponse<List<String>>>(
            """{"result":"error","error":{"code":"object_not_found","description":"Nope"}}"""
        )
        assertEquals(ApiResponseStatus.ERROR, response.result)
        assertEquals("object_not_found", response.error?.code)
    }

    @Test
    fun `cursor response round trips`() {
        val original = CursorApiResponse(
            result = ApiResponseStatus.SUCCESS,
            data = listOf("a", "b"),
            responseAt = 5,
            cursor = "c",
            hasMore = true,
        )
        val decoded = SerializationUtils.decodeFromString<CursorApiResponse<List<String>>>(
            SerializationUtils.encodeToString(original)
        )
        assertEquals(listOf("a", "b"), decoded.data)
        assertEquals("c", decoded.cursor)
        assertTrue(decoded.hasMore)
        assertEquals(5L, decoded.responseAt)
    }

    @Test
    fun `rejects invalid API dates in drive models`() {
        assertThrows(Exception::class.java) {
            SerializationUtils.decodeFromString<IpsToken>("""{"uuid":"a","valid_until":"2024-01-01"}""")
        }
    }
}
