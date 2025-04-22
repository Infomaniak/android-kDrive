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

fun View.enableEdgeToEdge(
    shouldConsumeInsets: Boolean = false,
    withPadding: Boolean = false,
    withTop: Boolean = true,
    withBottom: Boolean = true,
    customBehavior: ((Insets) -> Unit)? = null,
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
            val topInset = if (withTop) top else 0
            val bottomInset = if (withBottom) bottom else 0
            if (withPadding) {
                view.updatePadding(
                    left = left,
                    top = topInset,
                    right = right,
                    bottom = bottomInset,
                )
            } else {
                view.setMargins(
                    left = left,
                    top = topInset,
                    right = right,
                    bottom = bottomInset,
                )
            }

            customBehavior?.invoke(this)
        }

        if (shouldConsumeInsets) WindowInsetsCompat.CONSUMED else windowInsets
    }
}
