/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

data class BulkOperation(
    val action: BulkOperationType,
    val fileIds: List<Int>?,
    val exceptedFilesIds: List<Int>?,
    val parent: File,
    val destinationFolderId: Int?
) {

    fun toMap(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {

            put("action", action.toString().lowercase())

            destinationFolderId?.let { put("destination_directory_id", it) }

            fileIds?.let { put("file_ids", it) } ?: run {

                put("parent_id", parent.id)
                if (exceptedFilesIds?.isNotEmpty() == true) put("except_file_ids", exceptedFilesIds)
            }
        }
    }
}
