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
package com.infomaniak.drive.data.models.upload

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.utils.SerializationUtils
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UploadSerializationTest {

    @Test
    fun `decodes valid chunks with API field names and date`() {
        val validChunks = SerializationUtils.decodeFromString<ValidChunks>(
            """{"expected_chunks":1,"uploading_chunks":1,"failed_chunks":0,"expected_size":128,
                "uploaded_size":128,"chunks":[{"number":0,"status":"ok","created_at":1792675175,
                    "size":128,"hash":"abc"}]}"""
        )

        assertEquals(1, validChunks.expectedChunksCount)
        assertEquals(UploadSegment.ChunkStatus.OK, validChunks.chunks.single().status)
        assertEquals(Date(1_792_675_175_000L), validChunks.chunks.single().createdAt)
        assertEquals(
            """{"expected_chunks":1,"uploading_chunks":1,"failed_chunks":0,"expected_size":128,"uploaded_size":128,"chunks":[{"number":0,"status":"ok","created_at":1792675175,"size":128,"hash":"abc"}]}""",
            SerializationUtils.encodeToString(validChunks)
        )
    }

    @Test
    fun `decodes valid chunks inside an API response`() {
        val response = SerializationUtils.decodeFromString<ApiResponse<ValidChunks>>(
            """{"result":"success","data":{"expected_chunks":1,"uploading_chunks":1,"failed_chunks":0,
                "expected_size":128,"uploaded_size":128,"chunks":[{"number":0,"status":"ok",
                "created_at":1792675175,"size":128,"hash":"abc"}]}}"""
        )

        assertEquals(1, response.data?.chunks?.size)
        assertEquals(UploadSegment.ChunkStatus.OK, response.data?.chunks?.single()?.status)
    }
}
