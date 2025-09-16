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

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.chip.Chip
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SearchFilter
import com.infomaniak.drive.data.models.SearchFilter.FilterKey
import com.infomaniak.drive.databinding.ItemSearchFilterChipBinding
import com.infomaniak.drive.ui.fileList.SearchFiltersAdapter.SearchFiltersViewHolder

class SearchFiltersAdapter(
    private val onFilterRemoved: (key: FilterKey, categoryId: Int?) -> Unit,
) : Adapter<SearchFiltersViewHolder>() {

    var filters = arrayListOf<SearchFilter>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchFiltersViewHolder {
        return SearchFiltersViewHolder(ItemSearchFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = filters.size

    override fun onBindViewHolder(holder: SearchFiltersViewHolder, position: Int) = with(holder.binding.root) {
        val filter = this@SearchFiltersAdapter.filters[position]
        setEndMargin(position)
        setIcon(filter)
        setName(filter)
        setClick(this, filter)
    }

    private fun Chip.setEndMargin(position: Int) {
        layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginEnd = if (position == this@SearchFiltersAdapter.filters.lastIndex) {
                0
            } else {
                resources.getDimensionPixelSize(R.dimen.marginStandardSmall)
            }
        }
    }

    private fun Chip.setIcon(filter: SearchFilter) {
        if (filter.icon == null) {
            setChipIconResource(R.drawable.round_empty)
            filter.tint?.toColorInt()?.let { chipIconTint = ColorStateList.valueOf(it) }
        } else {
            setChipIconResource(filter.icon)
        }
    }

    private fun Chip.setName(filter: SearchFilter) {
        text = filter.text
    }

    private fun setClick(chip: Chip, filter: SearchFilter) {
        chip.setOnClickListener {
            val index = filters.indexOf(filter)
            if (index > -1) {
                filters.removeAt(index)
                notifyItemRemoved(index)
                if (index == filters.size && index >= 1) notifyItemChanged(index - 1)
                onFilterRemoved(filter.key, filter.categoryId)
            }
        }
    }

    fun setItems(newFilters: ArrayList<SearchFilter>) {
        filters = newFilters
        notifyItemRangeChanged(0, itemCount)
    }

    class SearchFiltersViewHolder(val binding: ItemSearchFilterChipBinding) : ViewHolder(binding.root)
}
