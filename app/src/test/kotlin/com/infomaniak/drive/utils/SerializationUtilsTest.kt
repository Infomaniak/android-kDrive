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
package com.infomaniak.drive.utils

import com.infomaniak.core.network.api.ApiController
import com.infomaniak.drive.data.models.FileCheckResult
import com.infomaniak.drive.data.models.FileCount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SerializationUtilsTest {

    @Test
    fun `encode and decode serializable data class`() {
        val value = TestDto(id = 42, displayName = "kDrive")

        val json = SerializationUtils.encodeToString(value)

        assertEquals("""{"id":42,"display_name":"kDrive"}""", json)
        assertEquals(value, SerializationUtils.decodeFromString<TestDto>(json))
    }

    @Test
    fun `decode migrated DTOs with API field names`() {
        val fileCount = SerializationUtils.decodeFromString<FileCount>(
            """{"files":3,"count":5,"directories":2}"""
        )
        val fileCheckResult = SerializationUtils.decodeFromString<FileCheckResult>(
            """{"user_id":42,"is_conflict":true}"""
        )

        assertEquals(FileCount(files = 3, count = 5, folders = 2), fileCount)
        assertEquals(FileCheckResult(userId = 42, isConflict = true), fileCheckResult)
    }

    @Test
    fun `decode migrated DTOs with absent or null fields`() {
        assertEquals(FileCount(), SerializationUtils.decodeFromString<FileCount>("""{}"""))
        assertEquals(
            FileCount(),
            SerializationUtils.decodeFromString<FileCount>("""{"files":null,"count":null,"directories":null}""")
        )
        assertEquals(FileCheckResult(), SerializationUtils.decodeFromString<FileCheckResult>("""{}"""))
        assertEquals(
            FileCheckResult(),
            SerializationUtils.decodeFromString<FileCheckResult>("""{"user_id":null,"is_conflict":null}""")
        )
    }

    @Test
    fun `Gson keeps mapping API field names during gradual migration`() {
        val fileCount = ApiController.gson.fromJson(
            """{"files":3,"count":5,"directories":2}""",
            FileCount::class.java
        )
        val fileCheckResult =
            ApiController.gson.fromJson("""{"user_id":42,"is_conflict":true}""", FileCheckResult::class.java)

        assertEquals(FileCount(files = 3, count = 5, folders = 2), fileCount)
        assertEquals(FileCheckResult(userId = 42, isConflict = true), fileCheckResult)
    }

    @Serializable
    private data class TestDto(
        val id: Int,
        @SerialName("display_name") val displayName: String
    )
}
