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
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchCategoriesOwnershipFilter
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.SingleLiveEvent

class SearchFiltersViewModel : ViewModel() {

    var date = SingleLiveEvent<SearchDateFilter?>()
    var type = SingleLiveEvent<ConvertedType?>()
    var categories: List<Category>? = null
    var categoriesOwnership: SearchCategoriesOwnershipFilter? = null

    fun clearFilters() {
        date.value = null
        type.value = null
        categories = null
        categoriesOwnership = null
    }
}
