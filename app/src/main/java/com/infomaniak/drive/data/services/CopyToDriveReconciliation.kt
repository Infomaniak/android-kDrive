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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.models.file.FileExternalImport
import kotlinx.coroutines.withTimeoutOrNull

internal enum class CopyToDriveOutcome { SUCCESS, FAILURE, UNKNOWN }

/** Silence on MQTT says nothing about the remote import. Only a matching terminal server status is authoritative. */
internal suspend fun reconcileCopyToDrive(
    importId: Int,
    timeoutMillis: Long = 30_000L,
    loadPage: suspend (Int) -> ApiResponse<List<FileExternalImport>>,
): CopyToDriveOutcome = withTimeoutOrNull(timeoutMillis) {
    var page = 1
    while (true) {
        val response = loadPage(page)
        if (!response.isSuccess()) return@withTimeoutOrNull CopyToDriveOutcome.UNKNOWN
        val imports = response.data ?: return@withTimeoutOrNull CopyToDriveOutcome.UNKNOWN
        val matchingImport = imports.firstOrNull { it.id == importId }
        if (matchingImport != null) {
            return@withTimeoutOrNull when (matchingImport.status) {
                "done" -> CopyToDriveOutcome.SUCCESS
                "failed", "canceled" -> CopyToDriveOutcome.FAILURE
                else -> CopyToDriveOutcome.UNKNOWN
            }
        }
        if (imports.isEmpty() || page >= response.pages) break
        page++
    }
    CopyToDriveOutcome.UNKNOWN
} ?: CopyToDriveOutcome.UNKNOWN
