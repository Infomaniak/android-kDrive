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
package com.infomaniak.drive.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.infomaniak.drive.databinding.PreviewHeaderViewBinding
import com.infomaniak.drive.utils.getDefaultPeekHeight
import com.infomaniak.lib.core.utils.setMargins
import kotlin.math.max

class PreviewHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    val pageNumberChip: Chip
        get() = binding.pageNumberChip

    val openWithButton: MaterialButton
        get() = binding.openWithButton

    val editButton: MaterialButton
        get() = binding.editButton

    private val binding by lazy { PreviewHeaderViewBinding.inflate(LayoutInflater.from(context), this, true) }

    fun setup(
        onBackClicked: (() -> Unit)? = null,
        onOpenWithClicked: (() -> Unit)? = null,
        onEditClicked: (() -> Unit)? = null
    ) {
        binding.backButton.setOnClickListener { onBackClicked?.invoke() }
        binding.openWithButton.setOnClickListener { onOpenWithClicked?.invoke() }
        binding.editButton.apply {
            setOnClickListener { onEditClicked?.invoke() }
            isGone = onEditClicked == null
        }
    }

    fun setupWindowInsetsListener(
        rootView: View,
        bottomSheetBehavior: BottomSheetBehavior<View>?,
        bottomSheetView: View,
    ) = with(binding.header) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                setMargins(left = left, top = top, right = right)
                val topOffset = getTopOffset(bottomSheetView)
                bottomSheetBehavior?.apply {
                    peekHeight = rootView.context.getDefaultPeekHeight() + bottom

                    if (topOffset > 0) {
                        expandedOffset = topOffset
                        maxHeight = rootView.height - topOffset
                    }
                }
                // Add padding to the bottom to allow the last element of the
                // list to be displayed right over the android navigation bar
                bottomSheetView.setPadding(0, 0, 0, bottom)
            }

            windowInsets
        }
    }

    private fun getTopOffset(bottomSheetView: View): Int {
        return if (rootView.height < bottomSheetView.height) max(top, rootView.height - bottomSheetView.height) else 0
    }
}
