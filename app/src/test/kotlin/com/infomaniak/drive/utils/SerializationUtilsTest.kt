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

    @Serializable
    private data class TestDto(
        val id: Int,
        @SerialName("display_name") val displayName: String
    )
}
