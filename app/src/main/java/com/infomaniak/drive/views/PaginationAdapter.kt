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
package com.infomaniak.drive.views

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.lib.core.views.ViewHolder

abstract class PaginationAdapter<T> : RecyclerView.Adapter<ViewHolder>() {

    protected abstract val itemList: ArrayList<T>

    var isComplete = false
    private var showLoading = false

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder

    abstract override fun onBindViewHolder(holder: ViewHolder, position: Int)

    fun clean() {
        itemList.clear()
        notifyDataSetChanged()
    }

    fun getItems(): ArrayList<T> = itemList

    override fun getItemCount() = itemList.size + if (showLoading) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return if (position < itemList.size) VIEW_TYPE_NORMAL
        else VIEW_TYPE_LOADING
    }

    private fun hideLoading() {
        showLoading = false
        notifyItemRemoved(itemCount)
    }

    fun showLoading() {
        if (!showLoading) {
            showLoading = true
            notifyItemInserted(itemCount - 1)
        }
    }

    fun addAll(newItemList: ArrayList<T>) {
        val beforeItemCount = itemCount
        itemList.addAll(newItemList)
        hideLoading()
        notifyItemRangeInserted(beforeItemCount, itemCount)
    }

    fun setList(newItemList: ArrayList<T>) {
        itemList.clear()
        itemList.addAll(newItemList)
        hideLoading()
        notifyDataSetChanged()
    }

    companion object {

        const val VIEW_TYPE_LOADING = 1
        const val VIEW_TYPE_NORMAL = 2

        fun createLoadingViewHolder(parent: ViewGroup): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false)
            )
        }
    }
}