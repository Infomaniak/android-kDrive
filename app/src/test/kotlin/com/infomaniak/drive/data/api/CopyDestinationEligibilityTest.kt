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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.cache.filterEligibleDestinations
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.utils.SerializationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopyDestinationEligibilityTest {

    @Test
    fun `decoded drive envelope preserves eligible destinations and excludes source and maintenance`() {
        val response = SerializationUtils.decodeFromString<ApiResponse<DriveInfo>>(
            """{"result":"success","data":{"ips":{"uuid":"fixture","valid_until":1792675175},"drives":[
                {"id":1,"name":"Source","role":"admin","in_maintenance":false},
                {"id":2,"name":"Destination","role":"user","in_maintenance":false},
                {"id":3,"name":"Maintenance","role":"admin","in_maintenance":true},
                {"id":4,"name":"Destination with defaults","role":"user"}
            ]}}"""
        )
        assertTrue(response.isSuccess())
        val drives = response.data!!.drives
        assertEquals(DriveUser.Role.ADMIN, drives[0].role)
        assertEquals(DriveUser.Role.USER, drives[1].role)
        assertEquals(listOf(2, 4), drives.filterEligibleDestinations(1).map { it.id })
        assertTrue(drives[2].maintenance)
        assertFalse(drives[3].maintenance)
    }

    @Test
    fun `null maintenance uses existing false default and shared role remains external`() {
        val response = SerializationUtils.decodeFromString<ApiResponse<DriveInfo>>(
            """{"result":"success","data":{"ips":{"uuid":"fixture","valid_until":1792675175},"drives":[
                {"id":1,"role":"admin"},
                {"id":2,"role":"user","in_maintenance":null},
                {"id":3,"role":"external"},
                {"id":4,"role":"none"}
            ]}}"""
        )
        val drives = response.data!!.drives
        assertEquals(DriveUser.Role.EXTERNAL, drives[2].role)
        assertEquals(DriveUser.Role.NONE, drives[3].role)
        // storeDriveInfos derives these cache fields from the authenticated user and role, not the API.
        drives.forEach {
            it.userId = 101
            it.sharedWithMe = it.role == DriveUser.Role.EXTERNAL
        }
        val sameAccountNonShared = drives.filter {
            it.userId == 101 && !it.sharedWithMe && it.role != DriveUser.Role.NONE
        }
        assertEquals(listOf(2), sameAccountNonShared.filterEligibleDestinations(1).map { it.id })
        assertTrue(sameAccountNonShared.filterEligibleDestinations(1).size == 1)
    }
}
