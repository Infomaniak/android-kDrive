/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.menu.GalleryPeriodBottomSheetAdapter.GalleryPeriodViewHolder

class GalleryPeriodBottomSheetAdapter(
    private val selectedPeriod: GalleryPeriod,
    private val onItemClicked: (period: GalleryPeriod) -> Unit,
) : Adapter<GalleryPeriodViewHolder>() {

    private val periods = GalleryPeriod.entries

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryPeriodViewHolder {
        return GalleryPeriodViewHolder(ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: GalleryPeriodViewHolder, position: Int) = with(holder.binding) {
        periods[position].let { period ->
            itemSelectText.setText(period.translation)
            itemSelectActiveIcon.isVisible = selectedPeriod == period
            root.setOnClickListener { onItemClicked(period) }
        }
    }

    override fun getItemCount() = periods.size

    class GalleryPeriodViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
