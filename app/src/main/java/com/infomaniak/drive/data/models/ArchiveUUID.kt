/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import com.google.gson.annotations.SerializedName

data class ArchiveUUID(val uuid: String) {

    class ArchiveBody private constructor(
        /** Array of files to include in the request; required without [parentId]. */
        @SerializedName("file_ids")
        var fileIds: IntArray? = null,

        /** The directory containing the files to include in the request; required without [fileIds]. */
        @SerializedName("parent_id")
        var parentId: Int? = null,

        /** Array of files to exclude from the request; only used when [parentId] is set, meaningless otherwise */
        @SerializedName("except_file_ids")
        var exceptFileIds: IntArray? = null,
    ) {
        constructor(fileIds: IntArray) : this(fileIds, null, null)
        constructor(parentId: Int, exceptFileIds: IntArray) : this(null, parentId, exceptFileIds)
    }
}
