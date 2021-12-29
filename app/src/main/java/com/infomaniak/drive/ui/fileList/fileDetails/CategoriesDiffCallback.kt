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
package com.infomaniak.drive.ui.fileList.fileDetails

import androidx.recyclerview.widget.DiffUtil
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.SelectedState

data class DiffUtilCategory(
    val id: Int,
    val selectedState: SelectedState,
)

class CategoriesDiffCallback(
    private val oldList: List<DiffUtilCategory>,
    private val newList: List<DiffUtilCategory>,
    private val isCreateRowVisible: Boolean,
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        return oldList[oldIndex].id == newList[newIndex].id
    }

    override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        return when {
            oldIndex == 0 && newIndex != 0 -> false // Remove top corners radius
            oldIndex != 0 && newIndex == 0 -> false // Add top Corners radius
            oldIndex == oldListSize - 1 && newIndex != newListSize - 1 -> false // Remove bot corners radius
            oldIndex != oldListSize - 1 && newIndex == newListSize - 1 -> false // Add bot corners radius
            oldList[oldIndex].selectedState != newList[newIndex].selectedState -> false // Update progress bar
            oldIndex == oldListSize - 1 && newIndex == newListSize - 1 && isCreateRowVisible -> false // Remove bot corners radius
            newIndex == newListSize - 1 && !isCreateRowVisible -> false // Add bot corners radius
            else -> true // Don't update
        }
    }
}
