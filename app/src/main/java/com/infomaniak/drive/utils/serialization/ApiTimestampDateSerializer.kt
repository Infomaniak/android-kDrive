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

import java.util.Date
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Matches Core's Gson date adapter: API dates are Unix timestamps in seconds. */
object ApiTimestampDateSerializer : KSerializer<Date> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ApiTimestampDate", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time / 1_000L)
    }

    override fun deserialize(decoder: Decoder): Date {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ApiTimestampDateSerializer requires a JSON decoder")
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            JsonNull -> throw SerializationException("Unexpected null for a non-null API date")
            is JsonPrimitive -> (element.longOrNull ?: element.content.toDoubleOrNull()?.toLong())
                ?.let { Date(it * 1_000L) }
                ?: throw SerializationException("Expected an integer Unix timestamp in seconds")
            else -> throw SerializationException("Expected an integer Unix timestamp in seconds")
        }
    }
}
