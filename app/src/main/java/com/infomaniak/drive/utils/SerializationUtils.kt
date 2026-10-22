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
import com.infomaniak.core.network.models.ApiResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object SerializationUtils {

    inline fun <reified T> decodeFromString(json: String): T = ApiController.json.decodeFromString(json)

    inline fun <reified T> encodeToString(value: T): String = ApiController.json.encodeToString(value)

    fun parseToJsonElement(json: String): JsonElement = ApiController.json.parseToJsonElement(json)

    /** Json used for request bodies: nulls are omitted. */
    val bodyJson: Json by lazy { Json(ApiController.json) { explicitNulls = false } }

    inline fun <reified T> encodeBody(value: T): String = bodyJson.encodeToString(value)

    /** Builds a diagnostic string of a response (generic [ApiResponse] can't be encoded without its data type). */
    fun describeForReport(response: ApiResponse<*>): String = buildJsonObject {
        put("result", response.result.name.lowercase())
        response.error?.let { error ->
            putJsonObject("error") {
                put("code", error.code)
                put("description", error.description)
                put("context", error.contextJson ?: JsonNull)
            }
        }
    }.toString()

    fun JsonObject.getStringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
