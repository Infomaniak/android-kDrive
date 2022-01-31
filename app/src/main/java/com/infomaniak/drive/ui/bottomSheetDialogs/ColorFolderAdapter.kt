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

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.view_color_round_button.view.*

class ColorFolderAdapter(
    private val onColorSelected: (color: String) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_color_round_button, parent, false))

    override fun getItemCount(): Int = COLORS.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.itemView.colorButtonView) {
        val color = ColorStateList.valueOf(COLORS[position].toColorInt())

        backgroundTintList = color
        iconTint = if (position == selectedPosition) ContextCompat.getColorStateList(context, R.color.white) else color

        setOnClickListener { onColorSelected(COLORS[position]) }
    }

    companion object {
        val COLORS = arrayListOf(
            "#9F9F9F",
            "#F44336",
            "#E91E63",
            "#9C26B0",
            "#673AB7",
            "#4051B5",
            "#4BAF50",
            "#009688",
            "#00BCD4",
            "#02A9F4",
            "#2196F3",
            "#8BC34A",
            "#CDDC3A",
            "#FFC10A",
            "#FF9802",
            "#607D8B",
            "#795548",
        )
    }
}
