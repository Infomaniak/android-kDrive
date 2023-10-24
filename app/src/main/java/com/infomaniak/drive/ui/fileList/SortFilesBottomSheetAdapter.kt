/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.SortTypeUsage
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.fileList.SortFilesBottomSheetAdapter.SortFilesViewHolder
import com.infomaniak.lib.core.views.ViewHolder

class SortFilesBottomSheetAdapter(
    private val selectedType: SortType,
    private val usage: SortTypeUsage,
    private val onItemClicked: (sortType: SortType) -> Unit
) : RecyclerView.Adapter<SortFilesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortFilesViewHolder {
        return SortFilesViewHolder(ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SortFilesViewHolder, position: Int) = with(holder.binding) {
        usage.types()[position].let { sortType ->
            itemSelectText.setText(sortType.translation)
            itemSelectActiveIcon.isVisible = selectedType == sortType
            root.setOnClickListener { onItemClicked(sortType) }
        }
    }

    override fun getItemCount() = usage.types().size

    private fun SortTypeUsage.types(): Array<SortType> {
        return when (this) {
            SortTypeUsage.FILE_LIST -> SortType.values().fileListTypes()
            SortTypeUsage.TRASH -> SortType.values().trashTypes()
        }
    }

    private fun Array<SortType>.fileListTypes(): Array<SortType> {
        return filter { it != SortType.OLDER_TRASHED && it != SortType.RECENT_TRASHED }.toTypedArray()
    }

    private fun Array<SortType>.trashTypes(): Array<SortType> {
        return filter { it != SortType.OLDER && it != SortType.RECENT }.toTypedArray()
    }

    class SortFilesViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
