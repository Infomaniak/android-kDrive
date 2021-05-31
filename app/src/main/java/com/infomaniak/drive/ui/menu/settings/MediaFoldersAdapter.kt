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
import androidx.constraintlayout.widget.ConstraintSet
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_media_folder.view.*

class MediaFoldersAdapter(
    private val onSwitchChanged: (mediaFolder: MediaFolder, isChecked: Boolean) -> Unit
) : LoaderAdapter<MediaFolder>() {

    init {
        numberItemLoader = 9
    }

    fun removeItemsById(idList: ArrayList<Long>) {
        idList.forEach { id ->
            itemList.indexOfFirst { it.id == id }.let { index ->
                if (index != -1) {
                    itemList.removeAt(index)
                    notifyItemRemoved(index)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_media_folder, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.apply {
            if (getItemViewType(position) == VIEW_TYPE_LOADING) {
                mediaFolderCardView.startLoading()
                mediaFolderNameLayout.layoutParams.width = 80.toPx()
            } else {
                itemList[position].let { mediaFolder ->
                    mediaFolderCardView.stopLoading()
                    mediaFolderTitle.text = mediaFolder.name
                    mediaFolderNameLayout.layoutParams.width = ConstraintSet.WRAP_CONTENT
                    mediaFolderDivider.visibility = if (position == itemCount - 1) GONE else VISIBLE
                    mediaFolderSwitch.apply {
                        isChecked = mediaFolder.isSynced
                        visibility = VISIBLE
                        setOnCheckedChangeListener { _, isChecked ->
                            if (mediaFolderSwitch.isPressed) {
                                onSwitchChanged(mediaFolder, isChecked)
                                mediaFolder.isSynced = isChecked
                            }
                        }
                    }
                }
            }
        }
    }
}