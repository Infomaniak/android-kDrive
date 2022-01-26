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
package com.infomaniak.drive.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.getName
import kotlinx.android.synthetic.main.view_categories_container.view.*

class CategoriesContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var categories: List<Category> = emptyList()
    private var canPutCategoryOnFile: Boolean = false
    private var onClicked: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_categories_container, this)
    }

    fun setup(categories: List<Category>, canPutCategoryOnFile: Boolean, layoutInflater: LayoutInflater, onClicked: () -> Unit) {
        this.categories = categories
        this.canPutCategoryOnFile = canPutCategoryOnFile
        this.onClicked = onClicked

        categorySwitch.isVisible = canPutCategoryOnFile
        setCategoryTitle()
        setClickListener()
        setCategories(layoutInflater)
    }

    private fun setCategoryTitle() {
        categoryTitle.setText(
            if (canPutCategoryOnFile) {
                if (categories.isEmpty()) R.string.addCategoriesTitle else R.string.manageCategoriesTitle
            } else {
                R.string.categoriesFilterTitle
            }
        )
    }

    private fun setClickListener() {
        if (canPutCategoryOnFile) {
            categoriesContainerView.setOnClickListener { onClicked?.invoke() }
        }
    }

    private fun setCategories(layoutInflater: LayoutInflater) = with(categoriesGroup) {
        if (categories.isEmpty()) {
            isGone = true
        } else {
            isVisible = true
            removeAllViews()
            categories.forEach { addView(createChip(it, layoutInflater)) }
        }
    }

    @SuppressLint("InflateParams")
    private fun createChip(category: Category, layoutInflater: LayoutInflater): Chip {
        return (layoutInflater.inflate(R.layout.chip_category, null) as Chip).apply {
            text = category.getName(context)
            chipBackgroundColor = ColorStateList.valueOf(category.color.toColorInt())
            if (canPutCategoryOnFile) setOnClickListener { onClicked?.invoke() }
        }
    }
}
