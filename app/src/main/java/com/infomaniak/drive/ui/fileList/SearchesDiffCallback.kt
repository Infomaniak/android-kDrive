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

import androidx.recyclerview.widget.DiffUtil

class SearchesDiffCallback(
    private val oldList: List<String>,
    private val newList: List<String>,
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        return oldList[oldIndex].equals(newList[newIndex], true)
    }

    override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
        return when {
            oldIndex == 0 && newIndex != 0 -> false // Remove top corners radius
            oldIndex != 0 && newIndex == 0 -> false // Add top Corners radius
            oldIndex == oldListSize - 1 && newIndex != newListSize - 1 -> false // Remove bot corners radius
            oldIndex != oldListSize - 1 && newIndex == newListSize - 1 -> false // Add bot corners radius
            else -> true // Don't update
        }
    }
}
