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

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.*

data class SearchFilter(
    val key: FilterKey,
    val text: String,
    val icon: Int? = null,
    val tint: String? = null,
    val categoryId: Int? = null,
)

enum class FilterKey {
    DATE,
    TYPE,
    CATEGORIES,
    CATEGORIES_OWNERSHIP,
}

@Parcelize
data class SearchDateFilter(
    val key: DateFilterKey,
    val start: Date,
    val end: Date,
    val text: String,
) : Parcelable

@Parcelize
enum class DateFilterKey : Parcelable {
    TODAY,
    YESTERDAY,
    LAST_SEVEN_DAYS,
    CUSTOM,
}

@Parcelize
enum class CategoriesOwnershipFilter : Parcelable {
    BELONG_TO_ALL_CATEGORIES,
    BELONG_TO_ONE_CATEGORY,
}
