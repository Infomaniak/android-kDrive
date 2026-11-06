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
package com.infomaniak.drive.data.models

import com.infomaniak.drive.utils.SerializationUtils
import com.infomaniak.drive.utils.SerializationUtils.getStringOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MqttNotificationSerializationTest {

    @Test
    fun knownActionIsDecoded() {
        val json = """{"uid":"a","action":"file_move","drive_id":1,"file_id":2,"parent_id":3,"user_id":4}"""
        val notification = SerializationUtils.decodeFromString<MqttNotification>(json)
        assertEquals(MqttAction.FILE_MOVE, notification.action)
        assertEquals(2, notification.fileId)
    }

    @Test
    fun unknownActionIsNull() {
        val json = """{"uid":"a","action":"something_new","drive_id":1,"file_id":2,"parent_id":3,"user_id":4}"""
        assertNull(SerializationUtils.decodeFromString<MqttNotification>(json).action)
    }

    @Test
    fun importProgressIsDecoded() {
        val json = """{"uid":"a","action":"import_finished","drive_id":1,"file_id":2,"parent_id":3,"user_id":4,
            "import_progress":{"total_files":10,"total_files_processed":5,"total_successes":4,"total_errors":1}}"""
        val progress = SerializationUtils.decodeFromString<MqttNotification>(json).importProgress
        assertEquals(10, progress?.totalFiles)
        assertEquals(1, progress?.totalErrors)
    }

    @Test
    fun jsonObjectStringLookup() {
        val obj = SerializationUtils.parseToJsonElement("""{"error":"password_protected_error"}""").jsonObject
        assertEquals("password_protected_error", obj.getStringOrNull("error"))
        assertNull(obj.getStringOrNull("missing"))
    }
}
