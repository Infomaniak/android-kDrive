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

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Date

class ShareLinkSettingsSerializationTest {

    @Test
    fun `matches share link API field names and timestamp format`() {
        val json = buildShareLinkSettingsJsonElement(
            canDownload = true,
            canEdit = null,
            canSeeStats = false,
            password = "secret",
            right = ShareLink.ShareLinkFilePermission.PUBLIC,
            validUntil = Date(1_234_567L),
            isKSuiteFreeTier = false,
        )

        assertEquals(JsonPrimitive(true), json["can_download"])
        assertEquals(JsonNull, json["can_edit"])
        assertEquals(JsonPrimitive(false), json["can_see_stats"])
        assertEquals(JsonPrimitive("secret"), json["password"])
        assertEquals(JsonPrimitive("public"), json["right"])
        assertEquals(JsonPrimitive(1_234L), json["valid_until"])
    }

    @Test
    fun `omits unset optional fields and free-tier validity`() {
        val json = buildShareLinkSettingsJsonElement(
            canDownload = null,
            canEdit = null,
            canSeeStats = null,
            password = null,
            right = null,
            validUntil = Date(1_234_567L),
            isKSuiteFreeTier = true,
        )

        assertEquals(JsonNull, json["can_download"])
        assertEquals(JsonNull, json["can_edit"])
        assertEquals(JsonNull, json["can_see_stats"])
        assertNull(json["password"])
        assertNull(json["right"])
        assertNull(json["valid_until"])
    }
}
