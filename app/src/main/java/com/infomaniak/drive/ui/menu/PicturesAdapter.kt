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
package com.infomaniak.drive.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.loadUrl
import com.infomaniak.drive.views.PaginationAdapter
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_picture.view.*
import kotlinx.android.synthetic.main.title_recycler_section.view.*

class PicturesAdapter(
    override var itemList: ArrayList<Any> = arrayListOf(),
    private val onItemClick: (file: File) -> Unit
) : PaginationAdapter<Any>() {

    var lastSectionTitle: String = ""
    var pictureList: ArrayList<File> = arrayListOf()

    fun formatList(newPictureList: ArrayList<File>): ArrayList<Any> {
        pictureList.addAll(newPictureList)
        val addItemList: ArrayList<Any> = arrayListOf()

        for (picture in newPictureList) {
            val month = picture.getLastModifiedAt().format("MMMM yyyy")

            if (lastSectionTitle != month) {
                addItemList.add(month)
                lastSectionTitle = month
            }
            addItemList.add(picture)
        }

        return addItemList
    }

    override fun getItemViewType(position: Int): Int {
        return if (itemList[position] is File) {
            DisplayType.PICTURE.layout
        } else {
            DisplayType.TITLE.layout
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun clearPictures() {
        itemList.clear()
        pictureList.clear()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]
        when (getItemViewType(position)) {
            DisplayType.TITLE.layout -> {
                holder.itemView.apply {
                    title.text = (item as String)
                }
            }
            DisplayType.PICTURE.layout -> {
                val file = (item as File)

                holder.itemView.apply {
                    picture.loadUrl(file.thumbnail())
                    picture.contentDescription = file.name

                    setOnClickListener {
                        onItemClick(file)
                    }
                }
            }
        }
    }

    enum class DisplayType(val layout: Int) {
        TITLE(R.layout.title_recycler_section),
        PICTURE(R.layout.cardview_square_picture)
    }
}