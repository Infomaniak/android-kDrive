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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.content.Context
import android.util.TypedValue
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.utils.isPositive
import kotlin.math.max

class AutoSpanGridLayoutManager : GridLayoutManager {

    private var columnWidth = 0
    private var isColumnWidthChanged = true
    private var previousWidth = 0
    private var previousHeight = 0

    constructor(context: Context, columnWidth: Int) : super(context, 1) {
        setColumnWidth(checkedColumnWidth(context, columnWidth))
    }

    constructor(context: Context, columnWidth: Int, orientation: Int, reverseLayout: Boolean) : super(
        context, 1, orientation, reverseLayout
    ) {
        setColumnWidth(checkedColumnWidth(context, columnWidth))
    }

    override fun canScrollVertically(): Boolean = false

    override fun canScrollHorizontally(): Boolean = false

    private fun setColumnWidth(newColumnWidth: Int) {
        if (newColumnWidth > 0 && newColumnWidth != columnWidth) {
            columnWidth = newColumnWidth
            isColumnWidthChanged = true
        }
    }

    private fun checkedColumnWidth(context: Context, newColumnWidth: Int): Int {
        return (if (newColumnWidth.isPositive()) {
            newColumnWidth
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56.0f, context.resources.displayMetrics).toInt()
        }).also {
            columnWidth = it
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (columnWidth > 0 && width > 0 && height > 0
            && (isColumnWidthChanged || previousWidth != width || previousHeight != height)
        ) {
            val totalSpace = if (orientation == VERTICAL) {
                width - paddingRight - paddingLeft
            } else {
                height - paddingTop - paddingBottom
            }
            val spanCount = max(1, totalSpace / columnWidth)
            setSpanCount(spanCount)
            isColumnWidthChanged = false
        }
        previousWidth = width
        previousHeight = height
        super.onLayoutChildren(recycler, state)
    }
}
