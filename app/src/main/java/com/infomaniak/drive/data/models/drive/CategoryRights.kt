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
package com.infomaniak.drive.data.models.drive

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass(embedded = true)
open class CategoryRights(
    @SerializedName("can_create_category")
    var canCreateCategory: Boolean = false,
    @SerializedName("can_delete_category")
    var canDeleteCategory: Boolean = false,
    @SerializedName("can_edit_category")
    var canEditCategory: Boolean = false,
    @SerializedName("can_put_category_on_file")
    var canPutCategoryOnFile: Boolean = false,
    @SerializedName("can_read_category_on_file")
    var canReadCategoryOnFile: Boolean = false
) : RealmObject()
