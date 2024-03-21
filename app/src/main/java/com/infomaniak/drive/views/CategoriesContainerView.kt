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
import com.infomaniak.drive.databinding.ViewCategoriesContainerBinding
import com.infomaniak.drive.utils.getName

class CategoriesContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewCategoriesContainerBinding.inflate(LayoutInflater.from(context), this, true) }

    private var categories: List<Category> = emptyList()
    private var canPutCategoryOnFile: Boolean = false
    private var onClicked: (() -> Unit)? = null

    fun setup(categories: List<Category>, canPutCategoryOnFile: Boolean, layoutInflater: LayoutInflater, onClicked: () -> Unit) {
        this.categories = categories
        this.canPutCategoryOnFile = canPutCategoryOnFile
        this.onClicked = onClicked

        binding.categorySwitch.isVisible = canPutCategoryOnFile
        setCategoryTitle()
        setClickListener()
        setCategories(layoutInflater)
    }

    private fun setCategoryTitle() {
        binding.categoryTitle.setText(
            if (canPutCategoryOnFile) {
                if (categories.isEmpty()) R.string.addCategoriesTitle else R.string.manageCategoriesTitle
            } else {
                R.string.categoriesFilterTitle
            }
        )
    }

    private fun setClickListener() {
        if (canPutCategoryOnFile) {
            binding.categoriesContainerView.setOnClickListener { onClicked?.invoke() }
        }
    }

    private fun setCategories(layoutInflater: LayoutInflater) = with(binding.categoriesGroup) {
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
