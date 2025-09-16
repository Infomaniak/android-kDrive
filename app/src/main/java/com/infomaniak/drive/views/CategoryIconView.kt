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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.databinding.ItemCategoryIconBinding
import com.infomaniak.drive.utils.isPositive

class CategoryIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ItemCategoryIconBinding.inflate(LayoutInflater.from(context), this, true) }

    fun setCategoryIconOrHide(category: Category?) {
        category?.let {
            binding.categoryImageView.setBackgroundColor(it.color.toColorInt())
            isVisible = true
        } ?: run {
            isGone = true
        }
    }

    @SuppressLint("SetTextI18n")
    fun setRemainingCategoriesNumber(category: Category?, number: Int) = with(binding) {
        category?.let {
            remainingText.apply {
                if (number.isPositive()) {
                    text = "+$number"
                    isVisible = true
                } else {
                    isGone = true
                }
            }
            categoryImageView.setBackgroundColor(it.color.toColorInt())
            isVisible = true
        } ?: run {
            isGone = true
        }
    }
}
