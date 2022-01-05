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
package com.infomaniak.drive.ui.fileList

import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.models.CategoriesOwnershipFilter
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.drive.Category

class SearchFiltersViewModel : ViewModel() {

    var date: SearchDateFilter? = null
    var type: ConvertedType? = null
    var categories: List<Category>? = null
    var categoriesOwnership: CategoriesOwnershipFilter = DEFAULT_CATEGORIES_OWNERSHIP_VALUE

    fun clearFilters() {
        date = null
        type = null
        categories = null
        categoriesOwnership = DEFAULT_CATEGORIES_OWNERSHIP_VALUE
    }

    companion object {
        val DEFAULT_CATEGORIES_OWNERSHIP_VALUE = CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
    }
}
