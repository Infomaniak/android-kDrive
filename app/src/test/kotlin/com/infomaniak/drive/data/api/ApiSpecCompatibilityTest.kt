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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.api.publicshare.PublicShareToken
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.FileCheckResult
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.data.models.FileCount
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.models.file.LastFileAction
import com.infomaniak.drive.data.models.file.ListingFiles
import com.infomaniak.drive.data.models.upload.UploadSession
import com.infomaniak.drive.data.models.upload.UploadSession.StartUploadSession
import com.infomaniak.drive.data.models.upload.ValidChunks
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Decodes payloads modeled on the Infomaniak OpenAPI spec (`/2/drive`, `/3/drive`, `/2/app`, `/3/app` routes) with every
 * migrated response model. The fixtures in `api_spec_fixtures.json` are generated from the spec and come in four variants per
 * route: `full` and `alt` (all properties, with different `oneOf` branches / enum values), `required` (only the properties the
 * spec marks as required) and `nulls` (all optional properties set to `null`).
 */
class ApiSpecCompatibilityTest {

    private val decoders: Map<String, (String) -> Any> = mapOf(
        "File" to { SerializationUtils.decodeFromString<ApiResponse<File>>(it) },
        "PublicShareFile" to { SerializationUtils.decodeFromString<ApiResponse<File>>(it) },
        "FilesCursor" to { SerializationUtils.decodeFromString<CursorApiResponse<ArrayList<File>>>(it) },
        "PublicShareFiles" to { SerializationUtils.decodeFromString<CursorApiResponse<ArrayList<File>>>(it) },
        "ListingFiles" to { SerializationUtils.decodeFromString<CursorApiResponse<ListingFiles>>(it) },
        "ActivitiesCursor" to { SerializationUtils.decodeFromString<CursorApiResponse<ArrayList<FileActivity>>>(it) },
        "ActivitiesList" to { SerializationUtils.decodeFromString<ApiResponse<ArrayList<FileActivity>>>(it) },
        "LastActions" to { SerializationUtils.decodeFromString<ApiResponse<List<LastFileAction>>>(it) },
        "StartUploadSession" to { SerializationUtils.decodeFromString<ApiResponse<StartUploadSession>>(it) },
        "UploadSession" to { SerializationUtils.decodeFromString<ApiResponse<UploadSession>>(it) },
        "ValidChunks" to { SerializationUtils.decodeFromString<ApiResponse<ValidChunks>>(it) },
        "ShareLink" to { SerializationUtils.decodeFromString<ApiResponse<ShareLink>>(it) },
        "PublicShareLink" to { SerializationUtils.decodeFromString<ApiResponse<ShareLink>>(it) },
        "DropBox" to { SerializationUtils.decodeFromString<ApiResponse<DropBox>>(it) },
        "ShareableItems" to { SerializationUtils.decodeFromString<ApiResponse<Share>>(it) },
        "FileCheckResults" to { SerializationUtils.decodeFromString<ApiResponse<ArrayList<FileCheckResult>>>(it) },
        "FileComments" to { SerializationUtils.decodeFromString<CursorApiResponse<ArrayList<FileComment>>>(it) },
        "FileCount" to { SerializationUtils.decodeFromString<ApiResponse<FileCount>>(it) },
        "PublicShareFileCount" to { SerializationUtils.decodeFromString<ApiResponse<FileCount>>(it) },
        "Categories" to { SerializationUtils.decodeFromString<ApiResponse<List<Category>>>(it) },
        "DriveInfo" to { SerializationUtils.decodeFromString<ApiResponse<DriveInfo>>(it) },
        "CancellableAction" to { SerializationUtils.decodeFromString<ApiResponse<CancellableAction>>(it) },
        "ArchiveUUID" to { SerializationUtils.decodeFromString<ApiResponse<ArchiveUUID>>(it) },
        "FileExternalImports" to { SerializationUtils.decodeFromString<ApiResponse<List<FileExternalImport>>>(it) },
        "PublicShareImport" to { SerializationUtils.decodeFromString<ApiResponse<List<FileExternalImport>>>(it) },
        "PublicShareToken" to { SerializationUtils.decodeFromString<ApiResponse<PublicShareToken>>(it) },
        "Invitations" to { SerializationUtils.decodeFromString<ApiResponse<ArrayList<Invitation>>>(it) },
        "Boolean" to { SerializationUtils.decodeFromString<ApiResponse<Boolean>>(it) },
    )

    private fun loadFixtures(): JsonArray {
        val text = javaClass.classLoader.getResource("api_spec_fixtures.json")!!.readText()
        return Json.parseToJsonElement(text) as JsonArray
    }

    @TestFactory
    fun `every spec-modeled payload decodes without exception`(): List<DynamicTest> = loadFixtures().map { fixture ->
        val obj = fixture.jsonObject
        val model = obj.getValue("model").jsonPrimitive.content
        val route = obj.getValue("route").jsonPrimitive.content
        val variant = obj.getValue("variant").jsonPrimitive.content
        val json = obj.getValue("json").jsonPrimitive.content
        dynamicTest("$model <- $route [$variant]") {
            val decode = decoders[model] ?: error("No decoder registered for $model")
            decode(json)
        }
    }

    @Test
    fun `all fixtures have a registered decoder`() {
        val models = loadFixtures().map { it.jsonObject.getValue("model").jsonPrimitive.content }.toSet()
        assertEquals(emptySet<String>(), models - decoders.keys)
    }
}
