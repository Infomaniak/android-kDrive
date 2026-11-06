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
package com.infomaniak.drive.data.models.file

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileExternalImportTest {

    @Test
    fun nullableDirectoryIdUsesFallbackDestination() {
        val externalImport = Gson().fromJson(
            """{"directory_id":null}""",
            FileExternalImport::class.java,
        )

        assertNull(externalImport.directoryId)
        assertEquals(42, externalImport.destinationDirectoryIdOr(42))
    }

    @Test
    fun presentDirectoryIdOverridesFallbackDestination() {
        val externalImport = Gson().fromJson(
            """{"directory_id":123}""",
            FileExternalImport::class.java,
        )

        assertEquals(123, externalImport.directoryId)
        assertEquals(123, externalImport.destinationDirectoryIdOr(42))
    }
}
