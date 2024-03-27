/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.utils

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.lightNavigationBar

object PreviewUtils {

    fun Activity.setupBottomSheetFileBehavior(bottomSheetBehavior: BottomSheetBehavior<View>, isDraggable: Boolean) {
        setColorNavigationBar(true)
        bottomSheetBehavior.apply {
            isHideable = true
            this.isDraggable = isDraggable
            isFitToContents = true
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (bottomSheetBehavior.state) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            window?.navigationBarColor =
                                ContextCompat.getColor(this@setupBottomSheetFileBehavior, R.color.previewBackgroundTransparent)
                            window?.lightNavigationBar(false)
                        }
                        else -> {
                            setColorNavigationBar(true)
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            })
        }
    }
}