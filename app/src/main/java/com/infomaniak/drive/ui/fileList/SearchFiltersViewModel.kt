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
package com.infomaniak.drive.ui.fileList

import androidx.lifecycle.ViewModel
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.SearchCategoriesOwnershipFilter
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.drive.Category

class SearchFiltersViewModel : ViewModel() {

    var useInitialValues: Boolean = true
    val date = SingleLiveEvent<SearchDateFilter?>()
    val type = SingleLiveEvent<ExtensionType?>()
    var categories: List<Category>? = null
    var categoriesOwnership: SearchCategoriesOwnershipFilter? = null

    fun clearFilters() {
        useInitialValues = false
        date.value = null
        type.value = null
        categories = null
    }
}
