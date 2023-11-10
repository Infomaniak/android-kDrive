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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewColorRoundButtonBinding
import com.infomaniak.drive.ui.fileList.fileDetails.CreateOrEditCategoryAdapter.CategoriesBulletViewHolder

class CreateOrEditCategoryAdapter : Adapter<CategoriesBulletViewHolder>() {

    var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriesBulletViewHolder {
        return CategoriesBulletViewHolder(ViewColorRoundButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = COLORS.size

    override fun onBindViewHolder(holder: CategoriesBulletViewHolder, position: Int) = with(holder.binding.colorButtonView) {
        val color = ColorStateList.valueOf(COLORS[position].toColorInt())
        val white = ContextCompat.getColorStateList(context, R.color.white)

        backgroundTintList = color
        iconTint = if (position == selectedPosition) white else color

        setOnClickListener {
            iconTint = white
            val previousSelectedPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousSelectedPosition)
        }
    }

    companion object {
        val COLORS = arrayListOf(
            "#1ABC9C",
            "#11806A",
            "#2ECC71",
            "#128040",
            "#3498DB",
            "#206694",
            "#9B59B6",
            "#71368A",
            "#E91E63",
            "#AD1457",
            "#F1C40F",
            "#C27C0E",
            "#C45911",
            "#44546A",
            "#E74C3C",
            "#992D22",
            "#9D00FF",
            "#00B0F0",
            "#BE8F00",
            "#0B4899",
            "#009945",
            "#2E77B5",
            "#70AD47",
        )
    }

    class CategoriesBulletViewHolder(val binding: ViewColorRoundButtonBinding) : ViewHolder(binding.root)
}
