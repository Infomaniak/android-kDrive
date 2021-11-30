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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_category.view.*

class CategoriesAdapter(
    private val onCategoryChanged: (categoryID: Int, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var canEditCategory: Boolean = false
    var canDeleteCategory: Boolean = false

    var categories = emptyList<UICategory>()

    var onMenuClicked: ((category: UICategory) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_category, parent, false))

    fun setAll(newCategories: List<UICategory>) {
        categories = newCategories
            .sortedWith { a: UICategory, b: UICategory -> a.name.compareTo(b.name, true) }
            .sortedByDescending { it.isPredefined }
            .sortedByDescending { it.isSelected }
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.itemView.categoryCard.apply {

            var topCornerRadius = 0F
            var bottomCornerRadius = 0F
            if (position == 0) topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
            if (position == itemCount - 1) bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)

            shapeAppearanceModel = shapeAppearanceModel
                .toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .build()

            isCheckable = false
            categoryIcon.setBackgroundColor(Color.parseColor(category.color))
            checkIcon.isVisible = category.isSelected
            categoryTitle.text = category.name
            setOnClickListener { onCategoryChanged(category.id, !category.isSelected) }
            menuButton.isVisible = canEditCategory || (canDeleteCategory && !category.isPredefined)
            menuButton.setOnClickListener { onMenuClicked?.invoke(category) }
        }
    }

    override fun getItemCount() = categories.size

    data class UICategory(
        val id: Int,
        val name: String,
        val color: String,
        val isPredefined: Boolean,
        var isSelected: Boolean,
    )
}