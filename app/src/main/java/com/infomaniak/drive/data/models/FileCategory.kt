/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import java.util.*

@RealmClass(embedded = true)
open class FileCategory(
    var id: Int = -1,
    @SerializedName("ia_category_user_validation")
    var iaCategoryUserValidation: String = "",
    @SerializedName("is_generated_by_ia")
    var isGeneratedByIa: Boolean = false,
    @SerializedName("user_id")
    var userId: Int? = null,
    @SerializedName("added_to_file_at")
    var addedToFileAt: Date = Date(),
) : RealmObject()
