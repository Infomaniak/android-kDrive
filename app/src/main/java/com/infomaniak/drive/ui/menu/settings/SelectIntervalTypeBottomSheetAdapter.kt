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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_select_bottom_sheet.view.itemSelectActiveIcon
import kotlinx.android.synthetic.main.item_select_bottom_sheet.view.itemSelectText

class SelectIntervalTypeBottomSheetAdapter(
    private val selectedIntervalType: SyncSettings.IntervalType,
    private val onItemClicked: (intervalType: SyncSettings.IntervalType) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_select_bottom_sheet, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        SyncSettings.IntervalType.values()[position].let { intervalType ->
            holder.itemView.apply {
                itemSelectText.setText(intervalType.title)
                itemSelectActiveIcon.isVisible = selectedIntervalType == intervalType
                setOnClickListener { onItemClicked(intervalType) }
            }
        }
    }

    override fun getItemCount() = SyncSettings.IntervalType.values().size
}