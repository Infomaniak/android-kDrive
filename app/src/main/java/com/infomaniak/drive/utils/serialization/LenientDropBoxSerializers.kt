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

import com.infomaniak.drive.data.models.file.dropbox.DropBoxSize
import com.infomaniak.drive.data.models.file.dropbox.DropBoxValidity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * The OpenAPI spec types dropbox `capabilities.validity` and `capabilities.size` as strings while the API sends objects.
 * Objects are decoded normally; any other form (string, boolean, null) is ignored instead of failing the whole payload.
 */
object LenientDropBoxValiditySerializer : KSerializer<DropBoxValidity?> {

    private val delegate = DropBoxValidity.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: DropBoxValidity?) {
        if (value != null) encoder.encodeSerializableValue(delegate, value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): DropBoxValidity? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement() as? JsonObject ?: return null
        return jsonDecoder.json.decodeFromJsonElement(delegate, element)
    }
}

object LenientDropBoxSizeSerializer : KSerializer<DropBoxSize?> {

    private val delegate = DropBoxSize.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: DropBoxSize?) {
        if (value != null) encoder.encodeSerializableValue(delegate, value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): DropBoxSize? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement() as? JsonObject ?: return null
        return jsonDecoder.json.decodeFromJsonElement(delegate, element)
    }
}
