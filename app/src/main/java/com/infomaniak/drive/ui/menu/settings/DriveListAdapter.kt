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

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.CardviewDriveBinding
import com.infomaniak.drive.utils.AccountUtils

class DriveListAdapter(
    private var driveList: ArrayList<Drive>,
    private val hideCurrentDriveChevron: Boolean = true,
    private val onItemClicked: (drive: Drive) -> Unit,
) : Adapter<DriveListAdapter.DriveListViewHolder>() {

    fun setDrives(driveList: ArrayList<Drive>) {
        val max = this.driveList.size
        this.driveList = driveList
        notifyItemRangeChanged(0, maxOf(max, driveList.size))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriveListViewHolder {
        return DriveListViewHolder(CardviewDriveBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: DriveListViewHolder, position: Int) {
        driveList[position].let { drive ->
            holder.binding.apply {
                switchDrive.isGone = (drive.id == AccountUtils.currentDriveId) && hideCurrentDriveChevron
                driveName.text = drive.name
                driveIcon.imageTintList = ColorStateList.valueOf(drive.preferences.color.toColorInt())
                root.setOnClickListener { onItemClicked(drive) }
            }
        }
    }

    override fun getItemCount() = driveList.size

    class DriveListViewHolder(val binding: CardviewDriveBinding) : ViewHolder(binding.root)
}
