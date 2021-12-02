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
import android.util.AttributeSet
import android.widget.GridLayout
import androidx.core.view.children
import com.infomaniak.drive.R
import kotlin.math.max

class AutoResizeGridLayout : GridLayout {

    private var columnWidth = 0

    constructor(context: Context?) : super(context) {
        init(null, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs, defStyleAttr)
    }

    private fun init(attrs: AttributeSet?, defStyleAttr: Int) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.AutoResizeGridLayout, 0, defStyleAttr)
        try {
            columnWidth = array.getDimensionPixelSize(R.styleable.AutoResizeGridLayout_columnWidth, 0)
        } finally {
            array.recycle()
        }
        // Initially set columnCount to 1, it will be changed automatically later.
        columnCount = 1
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)

        val width = MeasureSpec.getSize(widthSpec)
        val oldColumnCount = columnCount
        val newColumnCount = if (columnWidth > 0 && width > 0) {
            max(1, width / columnWidth)
        } else {
            DEFAULT_COLUMN_COUNT
        }

        when {
            newColumnCount > oldColumnCount -> {
                columnCount = newColumnCount
                updateViewsPosition(newColumnCount)
            }
            newColumnCount < oldColumnCount -> {
                updateViewsPosition(newColumnCount)
                columnCount = newColumnCount
            }
        }
    }

    private fun updateViewsPosition(columns: Int) {
        var col = 0
        var row = 0
        children.forEach {
            it.layoutParams = (it.layoutParams as LayoutParams).apply {
                columnSpec = spec(col, 1, 1.0f)
                rowSpec = spec(row, 1, 1.0f)
            }
            if (++col == columns) {
                col = 0
                row++
            }
        }
        requestLayout()
    }

    private companion object {
        const val DEFAULT_COLUMN_COUNT = 7
    }
}
