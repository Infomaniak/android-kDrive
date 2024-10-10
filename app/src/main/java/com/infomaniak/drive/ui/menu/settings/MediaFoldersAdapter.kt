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
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.databinding.ItemMediaFolderBinding
import com.infomaniak.drive.ui.menu.settings.MediaFoldersAdapter.MediaFoldersViewHolder

class MediaFoldersAdapter(
    private val onSwitchChanged: (mediaFolder: MediaFolder, isChecked: Boolean) -> Unit,
) : Adapter<MediaFoldersViewHolder>() {

    private var itemList: ArrayList<MediaFolder> = arrayListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaFoldersViewHolder {
        return MediaFoldersViewHolder(ItemMediaFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MediaFoldersViewHolder, position: Int): Unit = with(holder.binding) {
        val mediaFolder = itemList[position]
        mediaFolderTitle.text = mediaFolder.name
        mediaFolderPath.apply {
            isGone = mediaFolder.name.isEmpty() || mediaFolder.path.isEmpty()
            text = mediaFolder.path
        }
        mediaFolderSwitch.apply {
            isChecked = mediaFolder.isSynced
            isVisible = true
            setOnCheckedChangeListener { _, isChecked ->
                if (mediaFolderSwitch.isPressed) {
                    onSwitchChanged(mediaFolder, isChecked)
                    mediaFolder.isSynced = isChecked
                }
            }
        }
    }

    override fun getItemCount(): Int = itemList.size

    fun addAll(newItemList: ArrayList<MediaFolder>) {
        val beforeItemCount = itemCount
        itemList.addAll(newItemList)
        notifyItemRangeInserted(beforeItemCount, newItemList.size)
    }

    fun removeItemsById(idList: List<Long>) {
        idList.forEach { id ->
            itemList.indexOfFirst { it.id == id }.let { index ->
                if (index != -1) {
                    itemList.removeAt(index)
                    notifyItemRemoved(index)
                }
            }
        }
    }

    class MediaFoldersViewHolder(val binding: ItemMediaFolderBinding) : ViewHolder(binding.root)
}
