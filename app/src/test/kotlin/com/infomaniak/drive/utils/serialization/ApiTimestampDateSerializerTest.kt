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
package com.infomaniak.drive.utils.serialization

import com.infomaniak.drive.utils.SerializationUtils
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApiTimestampDateSerializerTest {

    @Test
    fun `decodes Unix timestamp in seconds`() {
        val date = SerializationUtils.decodeFromString<DateValue>("""{"date":1792675175}""").date

        assertEquals(Date(1_792_675_175_000L), date)
    }

    @Test
    fun `encodes Unix timestamp in seconds`() {
        val json = SerializationUtils.encodeToString(DateValue(Date(1_792_675_175_999L)))

        assertEquals("""{"date":1792675175}""", json)
    }

    @Test
    fun `missing date is rejected`() {
        assertThrows(SerializationException::class.java) {
            SerializationUtils.decodeFromString<DateValue>("""{}""")
        }
    }

    @Test
    fun `unexpected null date is rejected`() {
        assertThrows(SerializationException::class.java) {
            SerializationUtils.decodeFromString<DateValue>("""{"date":null}""")
        }
    }

    @Test
    fun `invalid date format is rejected`() {
        assertThrows(SerializationException::class.java) {
            SerializationUtils.decodeFromString<DateValue>("""{"date":"not-a-timestamp"}""")
        }
    }

    @Test
    fun `quoted numeric timestamp is accepted like Gson did`() {
        assertEquals(Date(1_792_675_175_000L), SerializationUtils.decodeFromString<DateValue>("""{"date":"1792675175"}""").date)
    }

    @Test
    fun `non numeric string timestamp is rejected`() {
        assertThrows(SerializationException::class.java) {
            SerializationUtils.decodeFromString<DateValue>("""{"date":"2026-01-01"}""")
        }
    }

    @Test
    fun `decodes negative Unix timestamp`() {
        val date = SerializationUtils.decodeFromString<DateValue>("""{"date":-1}""").date

        assertEquals(Date(-1_000L), date)
    }

    @Serializable
    private data class DateValue(
        @Serializable(with = ApiTimestampDateSerializer::class)
        val date: Date
    )
}
