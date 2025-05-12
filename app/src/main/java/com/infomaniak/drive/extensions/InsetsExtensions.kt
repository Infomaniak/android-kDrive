/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.extensions

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.infomaniak.lib.core.utils.setMargins

fun View.onApplyWindowInsetsListener(
    shouldConsumeInsets: Boolean = false,
    callback: (View, Insets) -> Unit,
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val combinedInsets = getInsetsFrom(windowInsets)

        callback(view, combinedInsets)

        if (shouldConsumeInsets) WindowInsetsCompat.CONSUMED else windowInsets
    }
}

private fun getInsetsFrom(windowInsetsCompat: WindowInsetsCompat): Insets {
    val systemBarsInsets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
    val cutoutInsets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.displayCutout())
    return Insets.max(systemBarsInsets, cutoutInsets)
}

fun View.enableEdgeToEdge(
    shouldConsumeInsets: Boolean = false,
    withPadding: Boolean = false,
    withLeft: Boolean = true,
    withTop: Boolean = true,
    withRight: Boolean = true,
    withBottom: Boolean = true,
    customBehavior: ((Insets) -> Unit)? = null,
) {
    onApplyWindowInsetsListener(shouldConsumeInsets) { view, windowInsets ->
        with(windowInsets) {
            val leftInset = left.getOrDefault(withRealInset = withLeft)
            val topInset = top.getOrDefault(withRealInset = withTop)
            val rightInset = right.getOrDefault(withRealInset = withRight)
            val bottomInset = bottom.getOrDefault(withRealInset = withBottom)

            if (withPadding) {
                view.updatePadding(
                    left = leftInset,
                    top = topInset,
                    right = rightInset,
                    bottom = bottomInset,
                )
            } else {
                view.setMargins(
                    left = leftInset,
                    top = topInset,
                    right = rightInset,
                    bottom = bottomInset,
                )
            }

            customBehavior?.invoke(this)
        }
    }
}

private fun Int.getOrDefault(withRealInset: Boolean): Int = if (withRealInset) this else 0
