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
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.menu.settings.SelectIntervalTypeBottomSheetAdapter.SelectIntervalTypeViewHolder

class SelectIntervalTypeBottomSheetAdapter(
    private val selectedIntervalType: SyncSettings.IntervalType,
    private val onItemClicked: (intervalType: SyncSettings.IntervalType) -> Unit,
) : Adapter<SelectIntervalTypeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectIntervalTypeViewHolder {
        return SelectIntervalTypeViewHolder(
            ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SelectIntervalTypeViewHolder, position: Int) = with(holder.binding) {
        SyncSettings.IntervalType.values()[position].let { intervalType ->
            itemSelectText.setText(intervalType.title)
            itemSelectActiveIcon.isVisible = selectedIntervalType == intervalType
            root.setOnClickListener { onItemClicked(intervalType) }
        }
    }

    override fun getItemCount() = SyncSettings.IntervalType.values().size

    class SelectIntervalTypeViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
