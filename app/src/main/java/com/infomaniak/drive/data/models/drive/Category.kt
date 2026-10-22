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
package com.infomaniak.drive.data.models.drive

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import com.infomaniak.drive.utils.serialization.ApiTimestampDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
@RealmClass(embedded = true)
open class Category(
    var id: Int = -1,
    var color: String = "#000000",
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerialName("created_at")
    @SerializedName("created_at")
    var createdAt: Date = Date(),
    @SerialName("is_predefined")
    @SerializedName("is_predefined")
    var isPredefined: Boolean = true,
    var name: String = "",
    @SerialName("user_usage_count")
    @SerializedName("user_usage_count")
    var userUsageCount: Int = 0,
) : RealmObject()
