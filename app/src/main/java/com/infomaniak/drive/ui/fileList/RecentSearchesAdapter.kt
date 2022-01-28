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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.fileList.FileAdapter.Companion.setCorners
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_search_result.view.*

class RecentSearchesAdapter(
    var searches: ArrayList<String>,
    private val onSearchClicked: (search: String) -> Unit,
) : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun getItemCount() = searches.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.itemView.searchResultCard) {
        val search = searches[position]
        setCorners(position, itemCount)
        searchResultText.text = search
        setOnClickListener { onSearchClicked(search) }
    }

    fun setItems(newSearches: List<String>) {
        DiffUtil.calculateDiff(SearchesDiffCallback(searches, newSearches)).dispatchUpdatesTo(this)
        searches = ArrayList(newSearches)
    }

    private class SearchesDiffCallback(
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
                oldIndex == oldList.lastIndex && newIndex != newList.lastIndex -> false // Remove bottom corners radius
                oldIndex != oldList.lastIndex && newIndex == newList.lastIndex -> false // Add bottom corners radius
                else -> true // Don't update
            }
        }
    }
}
