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

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return when {
            oldItemPosition == 0 && newItemPosition != 0 -> false // Remove top corners radius
            oldItemPosition != 0 && newItemPosition == 0 -> false // Add top Corners radius
            oldItemPosition == oldListSize - 1 && newItemPosition != newListSize - 1 -> false // Remove bot corners radius
            oldItemPosition != oldListSize - 1 && newItemPosition == newListSize - 1 -> false // Add bot corners radius
            oldList[oldItemPosition].selectedState != newList[newItemPosition].selectedState -> false // Update progress bar
            oldItemPosition == oldListSize - 1 && newItemPosition == newListSize - 1 && isCreateRowVisible -> false // Remove bot corners radius
            newItemPosition == newListSize - 1 && !isCreateRowVisible -> false // Add bot corners radius
            else -> true
        }
    }
}
