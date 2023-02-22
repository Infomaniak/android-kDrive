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
package com.infomaniak.drive.ui.menu.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_drive.view.driveIcon
import kotlinx.android.synthetic.main.cardview_drive.view.driveName
import kotlinx.android.synthetic.main.cardview_drive.view.switchDrive

class DriveListAdapter(
    var driveList: ArrayList<Drive>,
    private val hideCurrentDriveChevron: Boolean = true,
    private val onItemClicked: (drive: Drive) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    fun setDrives(driveList: ArrayList<Drive>) {
        val max = this.driveList.size
        this.driveList = driveList
        notifyItemRangeChanged(0, maxOf(max, driveList.size))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_drive, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        driveList[position].let { drive ->
            holder.itemView.apply {
                switchDrive.isGone = (drive.id == AccountUtils.currentDriveId) && hideCurrentDriveChevron
                driveName.text = drive.name
                driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
                setOnClickListener { onItemClicked(drive) }
            }
        }
    }

    override fun getItemCount() = driveList.size
}