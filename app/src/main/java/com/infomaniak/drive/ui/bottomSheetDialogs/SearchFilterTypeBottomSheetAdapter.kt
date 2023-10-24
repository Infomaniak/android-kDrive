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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.databinding.ItemSelectBottomSheetBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterTypeBottomSheetAdapter.SearchFilterTypeViewHolder
import com.infomaniak.lib.core.views.ViewHolder

class SearchFilterTypeBottomSheetAdapter(
    private val types: List<ExtensionType>,
    private val selectedType: ExtensionType?,
    private val onTypeClicked: (type: ExtensionType) -> Unit
) : RecyclerView.Adapter<SearchFilterTypeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchFilterTypeViewHolder {
        return SearchFilterTypeViewHolder(
            ItemSelectBottomSheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount() = types.size

    override fun onBindViewHolder(holder: SearchFilterTypeViewHolder, position: Int) = with(holder.binding) {
        types[position].let { type ->
            itemSelectIcon.apply {
                isVisible = true
                setImageResource(type.icon)
            }
            itemSelectText.setText(type.searchFilterName)
            itemSelectActiveIcon.isVisible = type.value == selectedType?.value
            root.setOnClickListener { onTypeClicked(type) }
        }
    }

    class SearchFilterTypeViewHolder(val binding: ItemSelectBottomSheetBinding) : ViewHolder(binding.root)
}
