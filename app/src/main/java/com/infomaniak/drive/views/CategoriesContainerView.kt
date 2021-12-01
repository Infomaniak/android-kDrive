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
package com.infomaniak.drive.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.utils.getName
import kotlinx.android.synthetic.main.view_categories_container.view.*

class CategoriesContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.view_categories_container, this)
    }

    fun setup(categories: List<Category>, categoryRights: CategoryRights?, onClicked: () -> Unit) {
        if (categoryRights?.canPutCategoryOnFile == true) {
            titleContainer.setOnClickListener { onClicked() }
            categorySwitch.isVisible = true
            categoryTitle.setText(if (categories.isEmpty()) R.string.addCategoriesTitle else R.string.manageCategoriesTitle)
        } else {
            categorySwitch.isGone = true
            categoryTitle.setText(R.string.categoriesFilterTitle)
        }

        if (categories.isEmpty()) {
            categoriesGroup.isVisible = false
        } else {
            categoriesGroup.removeAllViews()

            // Populate categories
            categories.forEach { category ->
                categoriesGroup.addView(
                    Chip(context).apply {
                        text = category.getName(context)
                        chipBackgroundColor = ColorStateList.valueOf(category.color.toColorInt())
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                    }
                )
            }

            // Show categories
            categoriesGroup.isVisible = true
        }
    }
}
