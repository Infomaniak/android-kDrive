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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.isPositive
import kotlinx.android.synthetic.main.item_category_icon.view.*

class CategoryIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.item_category_icon, this)
    }

    fun setCategoryIconOrHide(category: Category?) {
        category?.let {
            remainingText.isGone = true
            categoryImageView.setBackgroundColor(Color.parseColor(it.color))
            isVisible = true
        } ?: run {
            isGone = true
        }
    }

    @SuppressLint("SetTextI18n")
    fun setRemainingCategoriesNumber(number: Int, category: Category?) {
        category?.let {
            with(remainingText) {
                if (number.isPositive()) {
                    text = "+$number"
                    isVisible = true
                } else {
                    isGone = true
                }
            }
            categoryImageView.setBackgroundColor(Color.parseColor(it.color))
            isVisible = true
        } ?: run {
            isGone = true
        }
    }
}
