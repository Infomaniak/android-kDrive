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
package com.infomaniak.drive.ui.menu.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.data.models.SyncSettings.SavePicturesDate
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.menu.settings.SelectSaveDateBottomSheetAdapter.SelectSaveDateViewHolder

class SelectSaveDateBottomSheetAdapter(
    private val selectedSaveDate: SavePicturesDate,
    private val onItemClicked: (saveDate: SavePicturesDate) -> Unit,
) : Adapter<SelectSaveDateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectSaveDateViewHolder {
        return SelectSaveDateViewHolder(ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = SavePicturesDate.entries.size

    override fun onBindViewHolder(holder: SelectSaveDateViewHolder, position: Int) = with(holder.binding) {
        val saveDate = SavePicturesDate.entries[position]
        itemSelectText.setText(saveDate.title)
        itemSelectActiveIcon.isVisible = selectedSaveDate == saveDate
        root.setOnClickListener { onItemClicked(saveDate) }
    }

    class SelectSaveDateViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
