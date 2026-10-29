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
package com.infomaniak.drive.data.services

import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Only reports the shapes of known protocol fields, never their values or arbitrary field names. */
internal object MqttPayloadDiagnostics {
    fun describe(payload: String): String {
        val element = try {
            SerializationUtils.parseToJsonElement(payload)
        } catch (_: SerializationException) {
            return "payload=invalid_json"
        } catch (_: IllegalArgumentException) {
            return "payload=invalid_json"
        }
        val obj = element as? JsonObject ?: return "payload=${shape(element)}"
        val fields = listOf("uid", "drive_id", "action", "import_id", "user_id", "import_progress")
            .joinToString(" ") { "$it=${shape(obj[it])}" }
        val progress = obj["import_progress"] as? JsonObject ?: return fields
        return fields + " " + listOf("total_files", "total_files_processed", "total_successes", "total_errors")
            .joinToString(" ") { "import_progress.$it=${shape(progress[it])}" }
    }

    private fun shape(element: JsonElement?): String = when (element) {
        null -> "missing"
        JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            else -> "number_or_literal"
        }
    }
}
