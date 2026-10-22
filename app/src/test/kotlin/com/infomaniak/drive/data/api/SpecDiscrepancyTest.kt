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

import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.api.publicshare.PublicShareToken
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.models.drive.MaintenanceType.MaintenanceTypeValue
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Covers the places where the OpenAPI spec and the real/app payload shapes differ. */
class SpecDiscrepancyTest {

    private inline fun <reified T> decode(json: String) = ApiController.json.decodeFromString<ApiResponse<T>>(json)

    private fun dropBox(capabilities: String) = decode<DropBox>(
        """{"result":"success","data":{"id":1,"name":"d","capabilities":$capabilities,"created_at":1700000000}}"""
    ).data!!

    @Test
    fun dropBoxCapabilitiesAsObjects() {
        val caps = dropBox(
            """{"has_password":true,"has_validity":true,"has_size_limit":true,
            "validity":{"date":1700000000,"has_expired":false},"size":{"limit":100,"remaining":40}}"""
        ).capabilities!!
        assertEquals(1700000000_000L, caps.validity?.date?.time)
        assertEquals(false, caps.validity?.hasExpired)
        assertEquals(100L, caps.size?.limit)
        assertEquals(40L, caps.size?.remaining)
    }

    @Test
    fun dropBoxCapabilitiesAsStringsDoNotFail() {
        val caps = dropBox("""{"has_password":false,"validity":"2026-01-01","size":"100"}""").capabilities!!
        assertNull(caps.validity)
        assertNull(caps.size)
    }

    @Test
    fun dropBoxCapabilitiesNullOrAbsent() {
        assertNull(dropBox("""{"validity":null,"size":null}""").capabilities?.validity)
        assertNull(dropBox("""{"has_password":true}""").capabilities?.size)
    }

    private fun drive(maintenanceTypes: String?) = decode<Drive>(
        """{"result":"success","data":{"id":1,"name":"n"${maintenanceTypes?.let { ""","maintenance_types":$it""" } ?: ""}}}"""
    ).data!!

    @Test
    fun maintenanceTypesAsObjects() {
        val types = drive("""[{"name":"Asleep","code":"asleep","description":"zzz"},{"code":"waking_up"}]""").maintenanceTypes
        assertEquals(listOf(MaintenanceTypeValue.Asleep, MaintenanceTypeValue.WakingUp), types.map { it.type })
    }

    @Test
    fun maintenanceTypesAsPlainStrings() {
        val types = drive("""["asleep","hard_delete"]""").maintenanceTypes
        assertEquals(listOf(MaintenanceTypeValue.Asleep, MaintenanceTypeValue.HardDelete), types.map { it.type })
    }

    @Test
    fun maintenanceTypesSingleStringNullAndMixed() {
        assertEquals(MaintenanceTypeValue.Asleep, drive("\"asleep\"").maintenanceTypes.single().type)
        assertTrue(drive("null").maintenanceTypes.isEmpty())
        assertEquals(2, drive("""["asleep",{"code":"rewind"},42,null]""").maintenanceTypes.size)
    }

    @Test
    fun maintenanceTypeUnknownCodeAndMissingFields() {
        val types = drive("""[{"code":"brand_new_code"},{"name":"only name"},"also_new"]""").maintenanceTypes
        assertEquals(3, types.size)
        types.forEach { assertTrue(it.type == null || it.type == MaintenanceTypeValue.Unknown) }
    }

    @Test
    fun maintenanceTypesAbsent() {
        assertTrue(drive(null).maintenanceTypes.isEmpty())
    }

    @Test
    fun publicShareAuthTokenObject() {
        val response = decode<PublicShareToken>("""{"result":"success","data":{"token":"abc.def"}}""")
        assertTrue(response.isSuccess())
        assertEquals("abc.def", response.data?.token)
    }

    @Test
    fun publicShareAuthBooleanYieldsBlankToken() {
        // The app treats a blank token as an unknown error (PublicShareViewModel), never a crash.
        listOf("true", "false").forEach {
            val response = decode<PublicShareToken>("""{"result":"success","data":$it}""")
            assertTrue(response.isSuccess())
            assertTrue(response.data?.token.isNullOrBlank())
        }
    }

    @Test
    fun publicShareAuthErrorPayload() {
        val response = decode<PublicShareToken>(
            """{"result":"error","error":{"code":"password_not_valid","description":"Wrong password"}}"""
        )
        assertTrue(!response.isSuccess())
        assertNull(response.data)
        assertNotNull(response.error)
        assertEquals("password_not_valid", response.error?.code)
    }
}
