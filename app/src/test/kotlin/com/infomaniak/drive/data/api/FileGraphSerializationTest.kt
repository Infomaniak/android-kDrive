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
package com.infomaniak.drive.data.api

import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.FileActivityType
import com.infomaniak.drive.data.models.file.FileLastActivityBody
import com.infomaniak.drive.data.models.file.LastFileAction
import com.infomaniak.drive.data.models.upload.UploadSession.StartSessionBody
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileGraphSerializationTest {

    private inline fun <reified T> decode(json: String) = ApiController.json.decodeFromString<ApiResponse<T>>(json)

    @Test
    fun fileDerivesUidAndHandlesChildren() {
        val response = decode<File>(
            """{"result":"success","data":{"id":5,"drive_id":9,"name":"dir","type":"dir","parent_id":1,
            "created_at":1700000000,"unknown":{"a":1},"users":[1,2],"supported_by":["a","b"],
            "children":[{"id":6,"drive_id":9,"name":"c","type":"file","size":12,"status":null,"is_favorite":true}]}}"""
        )
        val file = response.data!!
        assertEquals("5_9", file.uid)
        assertEquals(listOf(1, 2), file.users.toList())
        assertEquals(1700000000_000L, file.createdAt * 1000)
        assertEquals("6_9", file.children.single().uid)
        assertEquals(12L, file.children.single().size)
        assertTrue(file.children.single().isFavorite)
        assertEquals("dir", file.sortedName)
    }

    @Test
    fun fileToleratesSloppyValues() {
        val file = decode<File>(
            """{"result":"success","data":{"id":"7","drive_id":3,"name":null,"size":"42","supported_by":"onlyoffice",
            "children":null,"categories":null,"sharelink":null,"capabilities":null,"visibility":null}}"""
        ).data!!
        assertEquals(7, file.id)
        assertEquals(42L, file.size)
        assertEquals(listOf("onlyoffice"), file.supportedBy?.toList())
        assertEquals("7_3", file.uid)
        assertTrue(file.children.isEmpty())
        assertNull(file.shareLink)
    }

    @Test
    fun localOnlyFieldsAreIgnored() {
        val file = decode<File>("""{"result":"success","data":{"id":1,"drive_id":1,"is_offline":true,"cursor":"x"}}""").data!!
        assertTrue(!file.isOffline)
        assertNull(file.cursor)
    }

    @Test
    fun unknownActivityTypeIsNotFatal() {
        val activity = decode<List<FileActivity>>(
            """{"result":"success","data":[{"id":1,"action":"file_brand_new_action","created_at":1700000000,"new_path":null}]}"""
        ).data!!
        assertEquals(1, activity.size)
        assertEquals("file_brand_new_action", activity.single().action)
        assertEquals(FileActivityType.FILE_CREATE, FileActivityType.fromAction("file_create"))
        assertNull(FileActivityType.fromAction("nope"))
    }

    @Test
    fun lastFileActionWithUnknownAction() {
        val actions = decode<List<LastFileAction>>(
            """{"result":"success","data":[{"file_id":3,"last_action":"unknown_thing","last_action_at":5,"file":{"id":3,"drive_id":1}}]}"""
        ).data!!
        assertNull(actions.single().lastAction)
        assertEquals("3_1", actions.single().file?.uid)
    }

    @Test
    fun cursorPaginationCarriesFiles() {
        val response = ApiController.json.decodeFromString<CursorApiResponse<List<File>>>(
            """{"result":"success","data":[{"id":1,"drive_id":2},{"id":2,"drive_id":2}],"cursor":"abc","has_more":true,"response_at":9}"""
        )
        assertEquals(2, response.data?.size)
        assertEquals("abc", response.cursor)
        assertTrue(response.hasMore)
        assertEquals("2_2", response.data?.last()?.uid)
    }

    @Test
    fun requestBodiesKeepTheirWireFormat() {
        val last = Json.parseToJsonElement(
            SerializationUtils.encodeBody(
                FileLastActivityBody(files = listOf(FileLastActivityBody.FileActionBody(id = 4, fromDate = 10)))
            )
        ).jsonObject
        assertEquals(
            listOf("file_delete", "file_trash", "file_move_out", "file_update", "file_rename", "file_rename_alias"),
            last["actions"]!!.let { (it as kotlinx.serialization.json.JsonArray).map { e -> e.jsonPrimitive.content } },
        )
        assertEquals("4", ((last["files"] as kotlinx.serialization.json.JsonArray)[0].jsonObject["id"])!!.jsonPrimitive.content)
        assertEquals("10", ((last["files"] as kotlinx.serialization.json.JsonArray)[0].jsonObject["from_date"])!!.jsonPrimitive.content)

        val archive = Json.parseToJsonElement(SerializationUtils.encodeBody(ArchiveUUID.ArchiveBody(7, intArrayOf(1, 2)))).jsonObject
        assertEquals("7", archive["parent_id"]!!.jsonPrimitive.content)
        assertEquals(2, (archive["except_file_ids"] as kotlinx.serialization.json.JsonArray).size)
        assertTrue("file_ids" !in archive)
    }

    @Test
    fun startSessionBodyUsesSnakeCaseNames() {
        val json = SerializationUtils.encodeBody(
            StartSessionBody(
                conflict = com.infomaniak.drive.data.api.UploadTask.Companion.ConflictOption.VERSION,
                createdAt = null,
                directoryId = 2,
                subDirectoryPath = "",
                fileName = "a.txt",
                lastModifiedAt = 3,
                totalChunks = 1,
                totalSize = 5,
            )
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("a.txt", obj["file_name"]!!.jsonPrimitive.content)
        assertEquals("5", obj["total_size"]!!.jsonPrimitive.content)
        assertEquals("1", obj["total_chunks"]!!.jsonPrimitive.content)
        assertEquals("2", obj["directory_id"]!!.jsonPrimitive.content)
        assertEquals("version", obj["conflict"]!!.jsonPrimitive.content)
    }
}
