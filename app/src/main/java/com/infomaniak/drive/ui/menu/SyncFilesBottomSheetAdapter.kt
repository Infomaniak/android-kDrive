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
package com.infomaniak.drive.ui.menu

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.databinding.ItemSelectSyncOptionBottomSheetBinding
import com.infomaniak.drive.ui.menu.SyncFilesBottomSheetAdapter.SyncFilesViewHolder
import com.infomaniak.drive.views.SyncFilesBottomSheetDialog.Companion.SyncFilesOption

class SyncFilesBottomSheetAdapter(
    private val syncOptions: SyncFilesOption,
    private val onItemClicked: (saveDate: SyncFilesOption) -> Unit,
    private val context: Context,
) : Adapter<SyncFilesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncFilesViewHolder {
        return SyncFilesViewHolder(
            ItemSelectSyncOptionBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount() = SyncFilesOption.entries.size

    override fun onBindViewHolder(holder: SyncFilesViewHolder, position: Int) = with(holder.binding) {
        val syncOption = SyncFilesOption.entries[position]
        itemSelectText.text = context.getString(syncOption.titleRes)
        itemSelectDescription.text = context.getString(syncOption.descriptionRes)
        itemSelectActiveIcon.isVisible = syncOptions == syncOption
        root.setOnClickListener { onItemClicked(syncOption) }
    }

    class SyncFilesViewHolder(val binding: ItemSelectSyncOptionBottomSheetBinding) : ViewHolder(binding.root)
}
