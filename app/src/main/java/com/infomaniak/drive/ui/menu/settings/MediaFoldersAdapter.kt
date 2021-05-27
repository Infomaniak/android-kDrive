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
package com.infomaniak.drive.ui.menu.settings

import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_media_folder.view.*

class MediaFoldersAdapter(
    private val onSwitchChanged: (mediaFolder: MediaFolder, isChecked: Boolean) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    private var mediaFolderList: ArrayList<MediaFolder> = ArrayList()

    fun setMediaFolders(mediaFolders: ArrayList<MediaFolder>) {
        this.mediaFolderList = mediaFolders
        notifyItemRangeChanged(0, maxOf(itemCount, mediaFolders.size))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_media_folder, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        mediaFolderList[position].let { mediaFolder ->
            holder.itemView.apply {
                mediaFolderTitle.text = mediaFolder.name
                mediaFolderSwitch.isChecked = mediaFolder.isSynced
                mediaFolderDivider.visibility = if (position == mediaFolderList.size - 1) GONE else VISIBLE
                mediaFolderSwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (mediaFolderSwitch.isPressed) {
                        onSwitchChanged(mediaFolder, isChecked)
                        mediaFolderList[position].isSynced = isChecked
                    }
                }
            }
        }
    }

    override fun getItemCount() = mediaFolderList.size
}