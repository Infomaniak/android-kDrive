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
package com.infomaniak.drive.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.SwitchDriveBottomSheetAdapter.SwitchDriveViewHolder
import com.infomaniak.drive.utils.AccountUtils

class SwitchDriveBottomSheetAdapter(
    private var driveList: ArrayList<Drive>,
    private val onItemClicked: (drive: Drive) -> Unit,
) : Adapter<SwitchDriveViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchDriveViewHolder {
        return SwitchDriveViewHolder(ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = driveList.size

    override fun onBindViewHolder(holder: SwitchDriveViewHolder, position: Int) = with(holder.binding) {
        val drive = driveList[position]

        itemSelectIcon.apply {
            setImageResource(R.drawable.ic_drive)
            imageTintList = ColorStateList.valueOf(drive.preferences.color.toColorInt())
            isVisible = true
        }

        itemSelectText.text = drive.name
        itemSelectActiveIcon.isVisible = drive.id == AccountUtils.currentDriveId

        root.setOnClickListener { onItemClicked(drive) }
    }

    class SwitchDriveViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
