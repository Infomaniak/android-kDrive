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

import com.infomaniak.drive.utils.serialization.ApiTimestampDateSerializer
import java.util.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileComment(
    val id: Int = 0,
    val user: DriveUser = DriveUser(),
    var body: String = "",
    var liked: Boolean = false,
    var likes: ArrayList<DriveUser>? = null,
    val responses: List<FileComment> = emptyList(),
    @SerialName("parent_id")
    val parentId: Int = 0,
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerialName("updated_at")
    val updatedAt: Date = Date(0),
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerialName("created_at")
    val createdAt: Date = Date(0),
    @SerialName("likes_count")
    var likesCount: Int = 0,
    @SerialName("is_resolved")
    val isResolved: Boolean = false,
    @SerialName("responses_count")
    val responsesCount: Int = 0
)
