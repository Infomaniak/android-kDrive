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
package com.infomaniak.drive.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_select_bottom_sheet.view.*

class SwitchDriveBottomSheetAdapter(
    private var driveList: ArrayList<Drive>,
    private val onItemClicked: (drive: Drive) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_select_bottom_sheet, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        driveList[position].let { drive ->
            holder.itemView.apply {
                itemSelectIcon.apply {
                    setImageResource(R.drawable.ic_drive)
                    imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
                    isVisible = true
                }
                itemSelectText.text = drive.name
                itemSelectActiveIcon.isVisible = drive.id == AccountUtils.currentDriveId
                setOnClickListener { onItemClicked(drive) }
            }
        }
    }

    override fun getItemCount() = driveList.size
}