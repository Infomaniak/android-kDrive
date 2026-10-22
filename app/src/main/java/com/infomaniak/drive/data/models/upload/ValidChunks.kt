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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidChunks(
    @SerialName("expected_chunks")
    val expectedChunksCount: Int = 0,
    @SerialName("uploading_chunks")
    val uploadedChunkCount: Int = 0,
    @SerialName("failed_chunks")
    val failedChunks: Long = 0,
    @SerialName("expected_size")
    val expectedSize: Long = 0,
    @SerialName("uploaded_size")
    val uploadedSize: Long = 0,
    val chunks: ArrayList<UploadSegment> = arrayListOf()
) {
    inline val validChunksIds get() = chunks.map { it.number }

    inline val validChunkSize get() = chunks.firstOrNull()?.size ?: 0
}
