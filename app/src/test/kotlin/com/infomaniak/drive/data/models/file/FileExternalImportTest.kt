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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileExternalImportTest {

    private fun decodeImport(fields: String): FileExternalImport =
        SerializationUtils.decodeFromString<ApiResponse<List<FileExternalImport>>>(
            """{"result":"success","data":[{$fields}]}"""
        ).data!!.single()

    @Test
    fun nullableDirectoryIdUsesFallbackDestination() {
        val externalImport = decodeImport(""""directory_id":null""")

        assertNull(externalImport.directoryId)
        assertEquals(42, externalImport.destinationDirectoryIdOr(42))
    }

    @Test
    fun presentDirectoryIdOverridesFallbackDestination() {
        val externalImport = decodeImport(""""directory_id":123""")

        assertEquals(123, externalImport.directoryId)
        assertEquals(123, externalImport.destinationDirectoryIdOr(42))
    }

    @Test
    fun absentDirectoryIdUsesFallbackDestination() {
        val externalImport = decodeImport("")

        assertNull(externalImport.directoryId)
        assertEquals(42, externalImport.destinationDirectoryIdOr(42))
    }

    @Test
    fun nonPositiveDirectoryIdStillUsesFallbackDestination() {
        listOf(0, -1).forEach { directoryId ->
            val externalImport = decodeImport(""""directory_id":$directoryId""")

            assertEquals(directoryId, externalImport.directoryId)
            assertEquals(42, externalImport.destinationDirectoryIdOr(42))
        }
    }
}
