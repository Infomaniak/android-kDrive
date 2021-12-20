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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.FilterKey
import com.infomaniak.drive.data.models.SearchFilter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_search_filter.view.*

class SearchFiltersAdapter(
    private val onFilterRemoved: (key: FilterKey, categoryId: Int?) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var filters = arrayListOf<SearchFilter>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_filter, parent, false))
    }

    override fun getItemCount() = filters.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.itemView) {
        val filter = filters[position]
        setLayouts(position)
        setData(filter)
        setStates(filter)
        setListeners(filter)
    }

    private fun View.setLayouts(position: Int) {
        layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginEnd = if (position == filters.size - 1) 0 else resources.getDimensionPixelSize(R.dimen.marginStandardSmall)
        }
    }

    private fun View.setData(filter: SearchFilter) = with(filter) {
        icon?.let { filterIcon.setImageResource(it) }
        tint?.let { roundIcon.setBackgroundColor(Color.parseColor(it)) }
        filterName.text = text
    }

    private fun View.setStates(filter: SearchFilter) {
        filterIcon.isVisible = filter.icon != null
        roundIcon.isVisible = filter.tint != null
    }

    private fun View.setListeners(filter: SearchFilter) {
        filterClose.setOnClickListener {
            val index = filters.indexOf(filter)
            if (index > -1) {
                filters.removeAt(index)
                notifyItemRemoved(index)
                if (index == filters.size && index >= 1) notifyItemChanged(index - 1)
                onFilterRemoved(filter.key, filter.categoryId)
            }
        }
    }

    fun setItems(newFilters: List<SearchFilter>) {
        filters = ArrayList(newFilters)
        notifyItemRangeChanged(0, itemCount)
    }
}
