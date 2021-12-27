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

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.fileList.SearchFiltersViewModel
import java.util.*

class SearchFiltersBackup(val context: Context) {

    private fun getSearchFiltersBackup(): SharedPreferences {
        return context.getSharedPreferences("SearchFilters", Context.MODE_PRIVATE)
    }

    var date: SearchDateFilter?
        get() {
            with(getSearchFiltersBackup()) {
                val type = when (getString("dateType", null)) {
                    SearchDateFilterType.TODAY.name -> SearchDateFilterType.TODAY
                    SearchDateFilterType.YESTERDAY.name -> SearchDateFilterType.YESTERDAY
                    SearchDateFilterType.LAST_SEVEN_DAYS.name -> SearchDateFilterType.LAST_SEVEN_DAYS
                    SearchDateFilterType.CUSTOM.name -> SearchDateFilterType.CUSTOM
                    else -> null
                }
                val start = getLong("dateStart", -1L)
                val end = getLong("dateEnd", -1L)
                val text = getString("dateText", "")
                return if (type != null) {
                    SearchDateFilter(type, Date(start), Date(end), text!!)
                } else null
            }
        }
        set(value) {
            with(getSearchFiltersBackup().edit()) {
                putString("dateType", value?.type?.name)
                putLong("dateStart", value?.start?.time ?: -1L)
                putLong("dateEnd", value?.end?.time ?: -1L)
                putString("dateText", value?.text)
                apply()
            }
        }

    var type: ConvertedType?
        get() {
            return when (getSearchFiltersBackup().getString("type", null)) {
                ConvertedType.ARCHIVE.name -> ConvertedType.ARCHIVE
                ConvertedType.AUDIO.name -> ConvertedType.AUDIO
                ConvertedType.CODE.name -> ConvertedType.CODE
                ConvertedType.FOLDER.name -> ConvertedType.FOLDER
                ConvertedType.IMAGE.name -> ConvertedType.IMAGE
                ConvertedType.PDF.name -> ConvertedType.PDF
                ConvertedType.PRESENTATION.name -> ConvertedType.PRESENTATION
                ConvertedType.SPREADSHEET.name -> ConvertedType.SPREADSHEET
                ConvertedType.TEXT.name -> ConvertedType.TEXT
                ConvertedType.VIDEO.name -> ConvertedType.VIDEO
                else -> null
            }
        }
        set(value) {
            with(getSearchFiltersBackup().edit()) {
                putString("type", value?.name)
                apply()
            }
        }

    var categories: List<Category>?
        get() = Gson().fromJson(
            getSearchFiltersBackup().getString("categories", null),
            object : TypeToken<List<Category>>() {}.type,
        )
        set(value) {
            with(getSearchFiltersBackup().edit()) {
                putString("categories", Gson().toJson(value))
                apply()
            }
        }

    var categoriesOwnership: CategoriesOwnershipFilter
        get() {
            val name = getSearchFiltersBackup().getString(
                "categoriesOwnership",
                SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE.name
            )
            return when (name) {
                CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES.name -> CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
                CategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY.name -> CategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
                else -> SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE
            }
        }
        set(value) {
            with(getSearchFiltersBackup().edit()) {
                putString("categoriesOwnership", value.name)
                apply()
            }
        }
}
