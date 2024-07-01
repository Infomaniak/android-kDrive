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

import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.File

open class UploadSession(
    open val file: File,
    open val token: String,
    @SerializedName("result")
    open val isSuccess: Boolean,
    open val message: String? = null
) {

    class StartUploadSession(
        @SerializedName("directory_id")
        val directoryId: Long?,
        @SerializedName("directory_path")
        val directoryPath: String?,
        @SerializedName("file_name")
        val fileName: String?,
        @SerializedName("upload_url")
        val uploadHost: String,
        file: File,
        token: String,
        isSuccess: Boolean,
        message: String? = null
    ) : UploadSession(file, token, isSuccess, message)

    data class StartSessionBody(
        val conflict: UploadTask.Companion.ConflictOption,
        @SerializedName("created_at")
        val createdAt: Long?,
        @SerializedName("directory_id")
        val directoryId: Int,
        @SerializedName("directory_path")
        val subDirectoryPath: String,
        @SerializedName("file_name")
        val fileName: String,
        @SerializedName("last_modified_at")
        val lastModifiedAt: Long,
        @SerializedName("total_chunks")
        val totalChunks: Int,
        @SerializedName("total_size")
        val totalSize: Long,
    )
}
