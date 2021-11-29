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
package com.infomaniak.drive.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.RequestManager
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.loadGlideUrl
import com.infomaniak.drive.views.PaginationAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_picture.view.*

class HomePicturesAdapter(
    override var itemList: ArrayList<File> = arrayListOf(),
    private val glideRequestManager: RequestManager,
    private val onItemClick: (file: File) -> Unit
) : PaginationAdapter<File>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_picture, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = itemList[position]
        holder.itemView.apply {
            picture.loadGlideUrl(glideRequestManager, file.thumbnail())
            picture.contentDescription = file.name
            setOnClickListener {
                onItemClick(file)
            }
        }
    }
}