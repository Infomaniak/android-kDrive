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
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class ApiRouteModelsSerializationTest {

    private inline fun <reified T> decode(json: String) = SerializationUtils.decodeFromString<ApiResponse<T>>(json)

    @Test
    fun `decodes boolean responses and ignores unknown fields`() {
        val response = decode<Boolean>("""{"result":"success","data":true,"unknown":{"a":1}}""")
        assertEquals(ApiResponseStatus.SUCCESS, response.result)
        assertEquals(true, response.data)
        assertTrue(response.isSuccess())
    }

    @Test
    fun `decodes error responses without data`() {
        val response = decode<Boolean>(
            """{"result":"error","error":{"code":"object_not_found","description":"Not found"}}"""
        )
        assertEquals(ApiResponseStatus.ERROR, response.result)
        assertNull(response.data)
        assertEquals("object_not_found", response.error?.code)
    }

    @Test
    fun `decodes file count with absent fields`() {
        val count = decode<FileCount>("""{"result":"success","data":{"count":4,"directories":1}}""").data!!
        assertEquals(4, count.count)
        assertEquals(1, count.folders)
        assertEquals(0, count.files)
    }

    @Test
    fun `decodes file check results`() {
        val results = decode<ArrayList<FileCheckResult>>(
            """{"result":"success","data":[{"user_id":3,"is_conflict":true},{"user_id":4}]}"""
        ).data!!
        assertEquals(listOf(3, 4), results.map { it.userId })
        assertEquals(listOf(true, false), results.map { it.isConflict })
    }

    @Test
    fun `decodes cancellable action and defaults the local drive id`() {
        val action = decode<CancellableAction>(
            """{"result":"success","data":{"cancel_id":"abc","cancel_valid_until":1792675175}}"""
        ).data!!
        assertEquals("abc", action.cancelId)
        assertEquals(1_792_675_175L, action.cancelValidUntil)
        assertEquals(0, action.driveId)
        assertEquals(9, action.setDriveAndReturn(9).driveId)
    }

    @Test
    fun `decodes archive uuid`() {
        assertEquals("u-1", decode<ArchiveUUID>("""{"result":"success","data":{"uuid":"u-1"}}""").data?.uuid)
    }

    @Test
    fun `decodes category with timestamp date`() {
        val category = decode<Category>(
            """{"result":"success","data":{"id":2,"name":"Work","color":"#FF0000","created_at":1792675175,
                "is_predefined":false,"user_usage_count":3}}"""
        ).data!!
        assertEquals(2, category.id)
        assertEquals(Date(1_792_675_175_000L), category.createdAt)
        assertFalse(category.isPredefined)
        assertEquals(3, category.userUsageCount)
    }

    @Test
    fun `rejects a non numeric category date`() {
        assertThrows(Exception::class.java) {
            decode<Category>("""{"result":"success","data":{"id":2,"created_at":"yesterday"}}""")
        }
    }

    @Test
    fun `decodes share link with nullable date and local fields untouched`() {
        val link = decode<ShareLink>(
            """{"result":"success","data":{"url":"https://kdrive.test/s/1","file_id":12,"right":"public",
                "valid_until":1792675175,"access_blocked":true,
                "capabilities":{"can_edit":true,"can_see_stats":false,"can_download":true}}}"""
        ).data!!
        assertEquals("https://kdrive.test/s/1", link.url)
        assertEquals(12, link.fileId)
        assertEquals("public", link._right)
        assertEquals(Date(1_792_675_175_000L), link.validUntil)
        assertTrue(link.accessBlocked)
        assertTrue(link.capabilities!!.canEdit)
        assertNull(link.newPassword)
        assertNull(link.newRight)
    }

    @Test
    fun `decodes share link with null date and absent optionals`() {
        val link = decode<ShareLink>(
            """{"result":"success","data":{"url":"u","valid_until":null}}"""
        ).data!!
        assertNull(link.validUntil)
        assertNull(link.fileId)
        assertNull(link.capabilities)
    }

    @Test
    fun `decodes drop box with nested validity and size`() {
        val box = decode<DropBox>(
            """{"result":"success","data":{"id":8,"name":"Box","url":"u","uuid":"x","created_at":1792675175,
                "created_by":2,"last_uploaded_at":1792675999,"nb_users":5,"updated_at":null,
                "capabilities":{"has_password":true,"has_notification":false,"has_validity":true,"has_size_limit":true,
                "validity":{"date":1792675175,"has_expired":false},"size":{"limit":4096,"remaining":1024}}}}"""
        ).data!!
        assertEquals(8, box.id)
        assertEquals(Date(1_792_675_175_000L), box.createdAt)
        assertEquals(1_792_675_999L, box.lastUploadedAt)
        assertEquals(5, box.collaborativeUsersCount)
        assertNull(box.updatedAt)
        assertTrue(box.hasPassword)
        assertEquals(Date(1_792_675_175_000L), box.validUntil)
        assertEquals(4096L, box.limitFileSize)
        assertFalse(box.newPassword)
    }
}
