/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.data.models.FileActivityType

data class FileLastActivityBody(
    var actions: List<FileActivityType> = listOf(
        FileActivityType.FILE_DELETE,
        FileActivityType.FILE_TRASH,
        FileActivityType.FILE_MOVE_OUT,
        FileActivityType.FILE_UPDATE,
        FileActivityType.FILE_RENAME,
        FileActivityType.FILE_RENAME_ALIAS,
    ),
    var files: List<FileActionBody> = emptyList(),
) {
    data class FileActionBody(
        val id: Int,
        @SerializedName("from_date")
        val fromDate: Long,
    )
}
