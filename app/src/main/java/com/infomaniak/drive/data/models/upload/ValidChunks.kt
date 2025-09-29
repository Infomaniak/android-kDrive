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

data class ValidChunks(
    @SerializedName("expected_chunks")
    val expectedChunksCount: Int,
    @SerializedName("uploading_chunks")
    val uploadedChunkCount: Int,
    @SerializedName("failed_chunks")
    val failedChunks: Long,
    @SerializedName("expected_size")
    val expectedSize: Long,
    @SerializedName("uploaded_size")
    val uploadedSize: Long,
    val chunks: ArrayList<UploadSegment>
) {
    inline val validChunksIds get() = chunks.map { it.number }

    inline val validChunkSize get() = chunks.firstOrNull()?.size ?: 0
}
