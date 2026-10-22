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
package com.infomaniak.drive.data.models.upload

import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** API payload of the end of an upload session. All fields are lenient: only the surrounding API status matters. */
@Serializable
class UploadSession(
    val file: File? = null,
    val token: String? = null,
    @SerialName("result")
    val isSuccess: Boolean = false,
    val message: String? = null
) {

    @Serializable
    class StartUploadSession(
        @SerialName("directory_id")
        val directoryId: Long? = null,
        @SerialName("directory_path")
        val directoryPath: String? = null,
        @SerialName("file_name")
        val fileName: String? = null,
        @SerialName("upload_url")
        val uploadHost: String,
        val file: File? = null,
        val token: String? = null,
        @SerialName("result")
        val isSuccess: Boolean = false,
        val message: String? = null
    )

    @Serializable
    data class StartSessionBody(
        val conflict: UploadTask.Companion.ConflictOption,
        @SerialName("created_at")
        val createdAt: Long?,
        @SerialName("directory_id")
        val directoryId: Int,
        @SerialName("directory_path")
        val subDirectoryPath: String,
        @SerialName("file_name")
        val fileName: String,
        @SerialName("last_modified_at")
        val lastModifiedAt: Long,
        @SerialName("total_chunks")
        val totalChunks: Int,
        @SerialName("total_size")
        val totalSize: Long,
    )
}
