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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.databinding.ItemMediaFolderBinding
import com.infomaniak.drive.ui.menu.settings.MediaFoldersAdapter.MediaFoldersViewHolder

class MediaFoldersAdapter(
    private val onSwitchChanged: (mediaFolder: MediaFolder, isChecked: Boolean) -> Unit,
) : Adapter<MediaFoldersViewHolder>() {

    private val items = mutableListOf<MediaFolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaFoldersViewHolder {
        return MediaFoldersViewHolder(ItemMediaFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MediaFoldersViewHolder, position: Int): Unit = with(holder.binding) {
        val mediaFolder = items[position]

        var path = mediaFolder.path.substringBeforeLast(mediaFolder.name)
        if (!path.startsWith("/")) path = "/$path"

        mediaFolderTitle.text = mediaFolder.name

        mediaFolderPath.apply {
            isGone = mediaFolder.name.isEmpty() || mediaFolder.path.isEmpty()
            text = path
        }

        mediaFolderSwitch.apply {
            isChecked = mediaFolder.isSynced
            isVisible = true
            setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(mediaFolder, isChecked)
                mediaFolder.isSynced = isChecked
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addAll(addedItems: ArrayList<MediaFolder>) {

        val oldList = items.toList()
        items.addAll(addedItems)

        notifyAdapter(oldList, items)
    }

    fun removeItemsById(ids: List<Long>) {

        val oldList = items.toList()

        ids.forEach { id ->
            val index = items.indexOfFirst { it.id == id }
            if (index != -1) items.removeAt(index)
        }

        notifyAdapter(oldList, items)
    }

    private fun notifyAdapter(oldList: List<MediaFolder>, newList: List<MediaFolder>) {
        DiffUtil.calculateDiff(MediaFoldersDiffCallback(oldList, newList)).dispatchUpdatesTo(this)
    }

    class MediaFoldersViewHolder(val binding: ItemMediaFolderBinding) : ViewHolder(binding.root)

    private class MediaFoldersDiffCallback(
        private val oldList: List<MediaFolder>,
        private val newList: List<MediaFolder>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return newList[newIndex].id == oldList[oldIndex].id
        }

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            val oldItem = oldList[oldIndex]
            val newItem = newList[newIndex]

            return when {
                newItem.isSynced != oldItem.isSynced -> false
                newItem.name != oldItem.name -> false
                newItem.path != oldItem.path -> false
                else -> true // Don't update
            }
        }
    }
}
