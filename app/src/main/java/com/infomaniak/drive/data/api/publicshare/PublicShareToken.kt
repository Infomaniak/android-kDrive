/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.api.publicshare

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = PublicShareTokenSerializer::class)
@Parcelize
data class PublicShareToken(
    val token: String = "",
) : Parcelable

/** The OpenAPI spec documents the auth `data` as a boolean while the app reads a `token` object: accept both. */
object PublicShareTokenSerializer : KSerializer<PublicShareToken> {

    private val delegate = kotlinx.serialization.json.JsonElement.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PublicShareToken) {
        encoder.encodeSerializableValue(delegate, JsonObject(mapOf("token" to JsonPrimitive(value.token))))
    }

    override fun deserialize(decoder: Decoder): PublicShareToken {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val token = (element as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull
        return PublicShareToken(token ?: "")
    }
}
