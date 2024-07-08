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
import java.util.Date

data class FileComment(
    val id: Int,
    val user: DriveUser,
    var body: String,
    var liked: Boolean,
    var likes: ArrayList<DriveUser>?,
    val responses: List<FileComment>,
    @SerializedName("parent_id")
    val parentId: Int,
    @SerializedName("updated_at")
    val updatedAt: Date,
    @SerializedName("created_at")
    val createdAt: Date,
    @SerializedName("likes_count")
    var likesCount: Int,
    @SerializedName("is_resolved")
    val isResolved: Boolean,
    @SerializedName("responses_count")
    val responsesCount: Int
)
