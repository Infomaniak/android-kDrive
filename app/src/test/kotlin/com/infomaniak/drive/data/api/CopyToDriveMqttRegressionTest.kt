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
package com.infomaniak.drive.data.api

import com.infomaniak.drive.data.models.MqttAction
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.data.services.CopyToDriveProgressWorker
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.serialization.MissingFieldException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Protocol-shaped samples, not captured device events. Uses the same decoder and terminal mapping as the worker. */
class CopyToDriveMqttRegressionTest {
    private fun decode(action: String, importId: Int = 45, extra: String = ""): MqttNotification =
        SerializationUtils.decodeFromString(
            """{"uid":"fixture-event","drive_id":10,"user_id":202,"action":"$action","import_id":$importId$extra}"""
        )

    @Test
    fun `completion is successful only for the tracked import even with a different event user`() {
        val notification = decode("import_finished")
        assertEquals(MqttAction.EXTERNAL_IMPORT_FINISHED, notification.action)
        assertEquals(202, notification.userId)
        assertTrue(notification.isImportTerminated())
        assertEquals(true, CopyToDriveProgressWorker.terminalImportResult(notification, 45))
        assertNull(CopyToDriveProgressWorker.terminalImportResult(notification, 46))
    }

    @Test
    fun `error and cancellation finish the matching import as failed`() {
        listOf("import_errored", "import_canceled").forEach { action ->
            val notification = decode(action)
            assertTrue(notification.isImportTerminated())
            assertEquals(false, CopyToDriveProgressWorker.terminalImportResult(notification, 45))
            assertNull(CopyToDriveProgressWorker.terminalImportResult(notification, 46))
        }
    }

    @Test
    fun `progress including full percent never implies terminal success`() {
        val notification = decode(
            "import_file_created",
            extra = ""","import_progress":{"total_files":10,"total_files_processed":10,"total_successes":9,"total_errors":1}""",
        )
        assertEquals(100, notification.importProgress?.percent)
        assertEquals(1, notification.importProgress?.totalErrors)
        assertFalse(notification.isImportTerminated())
        assertNull(CopyToDriveProgressWorker.terminalImportResult(notification, 45))
        listOf("import_started", "import_canceling").forEach {
            assertNull(CopyToDriveProgressWorker.terminalImportResult(decode(it), 45))
        }
    }

    @Test
    fun `quoted import identifier correlates and unknown actions do not finish the transfer`() {
        val quoted = SerializationUtils.decodeFromString<MqttNotification>(
            """{"uid":"fixture","drive_id":"10","import_id":"45","action":"import_finished","extra":"ignored"}"""
        )
        assertEquals(true, CopyToDriveProgressWorker.terminalImportResult(quoted, 45))
        val unknown = decode("new_unknown_import_action")
        assertNull(unknown.action)
        assertNull(CopyToDriveProgressWorker.terminalImportResult(unknown, 45))
    }

    @Test
    fun `completion without import identifier cannot be correlated`() {
        val notification = SerializationUtils.decodeFromString<MqttNotification>(
            """{"uid":"fixture","drive_id":10,"action":"import_finished"}"""
        )
        assertNull(CopyToDriveProgressWorker.terminalImportResult(notification, 45))
    }

    @Test
    fun `missing required MQTT metadata fails decoding instead of fabricating success`() {
        assertThrows(MissingFieldException::class.java) {
            SerializationUtils.decodeFromString<MqttNotification>(
                """{"action":"import_finished","import_id":45}"""
            )
        }
    }

}
