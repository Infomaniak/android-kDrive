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
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.SortTypeUsage
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.fileList.SortFilesBottomSheetAdapter.SortFilesViewHolder

class SortFilesBottomSheetAdapter(
    private val selectedType: SortType,
    usage: SortTypeUsage,
    private val onItemClicked: (sortType: SortType) -> Unit,
) : Adapter<SortFilesViewHolder>() {

    private val types by lazy { usage.types() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortFilesViewHolder {
        return SortFilesViewHolder(ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SortFilesViewHolder, position: Int) = with(holder.binding) {
        types[position].let { sortType ->
            itemSelectText.setText(sortType.translation)
            itemSelectActiveIcon.isVisible = selectedType == sortType
            root.setOnClickListener { onItemClicked(sortType) }
        }
    }

    override fun getItemCount() = types.size

    private fun SortTypeUsage.types(): List<SortType> {
        return when (this) {
            SortTypeUsage.FILE_LIST -> SortType.entries.fileListTypes()
            SortTypeUsage.TRASH -> SortType.entries.trashTypes()
            SortTypeUsage.SEARCH -> searchTypes()
        }
    }

    private fun List<SortType>.fileListTypes(): List<SortType> {
        return filter { it != SortType.OLDER_TRASHED && it != SortType.RECENT_TRASHED }
    }

    private fun List<SortType>.trashTypes(): List<SortType> = filter { it != SortType.OLDER && it != SortType.RECENT }

    private fun searchTypes(): List<SortType> = listOf(SortType.OLDER, SortType.RECENT)

    class SortFilesViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
