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
package com.infomaniak.drive.ui.bottomSheetDialogs

import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.models.File.ConvertedType
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.drive.Category

class SearchFiltersViewModel : ViewModel() {

    var date: SearchDateFilter? = DEFAULT_DATE_VALUE
    var type: ConvertedType? = DEFAULT_TYPE_VALUE
    var categories: List<Category>? = DEFAULT_CATEGORIES_VALUE
    var categoriesOwnership = DEFAULT_CATEGORIES_OWNERSHIP_FILTER_VALUE

    fun clearFilters() {
        date = DEFAULT_DATE_VALUE
        type = DEFAULT_TYPE_VALUE
        categories = DEFAULT_CATEGORIES_VALUE
        categoriesOwnership = DEFAULT_CATEGORIES_OWNERSHIP_FILTER_VALUE
    }

    companion object {
        const val BELONG_TO_ALL_CATEGORIES_FILTER = 1
        const val BELONG_TO_ONE_CATEGORY_FILTER = 2

        private val DEFAULT_DATE_VALUE = null
        private val DEFAULT_TYPE_VALUE = null
        private val DEFAULT_CATEGORIES_VALUE = null
        const val DEFAULT_CATEGORIES_OWNERSHIP_FILTER_VALUE = BELONG_TO_ALL_CATEGORIES_FILTER
    }
}
