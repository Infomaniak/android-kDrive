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
import com.infomaniak.drive.utils.serialization.ApiTimestampDateSerializer
import java.util.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadSegment(
    val number: Int,
    val status: ChunkStatus,
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerializedName("created_at")
    @SerialName("created_at")
    val createdAt: Date,
    val size: Int,
    /** Chunk uploaded md5 hash, computed */
    val hash: String
) {

    @Serializable
    enum class ChunkStatus {
        @SerialName("error")
        @SerializedName("error")
        ERROR,

        @SerialName("ok")
        @SerializedName("ok")
        OK,

        @SerialName("uploading")
        @SerializedName("uploading")
        UPLOADING;

        override fun toString() = name.lowercase()
    }
}
