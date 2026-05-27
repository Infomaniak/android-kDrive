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
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.view.isVisible
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.PreviewHeaderViewBinding
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import kotlin.math.max

class PreviewHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { PreviewHeaderViewBinding.inflate(LayoutInflater.from(context), this, true) }

    private val parentConstraintLayout by lazy { parent as ConstraintLayout }

    private val baseConstraintSet by lazy {
        ConstraintSet().apply {
            clone(parentConstraintLayout)
        }
    }
    private val collapsedConstraintSet by lazy {
        ConstraintSet().apply {
            clone(baseConstraintSet)

            clear(R.id.header, ConstraintSet.TOP)
            connect(R.id.header, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }
    }
    // We do this because we try to match the animation duration of the BottomSheet when we toggle the fullscreen mode.
    private val transition by lazy {
        ChangeBounds().apply {
            duration = 125
        }
    }

    fun setup(
        onBackClicked: (() -> Unit)? = null,
        onOpenWithClicked: (() -> Unit)? = null,
        onEditClicked: (() -> Unit)? = null,
    ) = with(binding) {
        backButton.setup(onBackClicked)
        openWithButton.setup(onOpenWithClicked)
        editButton.setup(onEditClicked)
    }

    private fun MaterialButton.setup(clickAction: (() -> Unit)?) {
        if (clickAction != null) {
            setOnClickListener { clickAction() }
        }
        isVisible = clickAction != null
    }

    fun setupWindowInsetsListener(
        rootView: View,
        bottomSheetView: View,
        callback: ((insets: Insets?) -> Unit)? = null,
    ) {

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView)
        rootView.onApplyWindowInsetsListener { _, windowInsets ->
            // We add the same margins here on the left and right to have the view centered
            val topOffset = getTopOffset(bottomSheetView)
            bottomSheetBehavior.apply {
                peekHeight = getDefaultPeekHeight(windowInsets)

                if (topOffset > 0) {
                    expandedOffset = topOffset
                    maxHeight = rootView.height - topOffset
                }
            }
            // Add padding to the bottom to allow the last element of the
            // list to be displayed right over the android navigation bar
            bottomSheetView.setPadding(0, 0, 0, bottom)

            callback?.invoke(windowInsets)
        }
    }

    fun setPageNumberVisibility(isVisible: Boolean) = with(binding.pageNumberChip) {
        this.isVisible = isVisible
        // Set `isFocusable` here instead of in XML file because setting it in the XML doesn't seem to affect the Chip.
        isFocusable = false
    }

    fun setPageNumberValue(currentPage: Int, totalPage: Int) {
        binding.pageNumberChip.text = context.getString(R.string.previewPdfPages, currentPage, totalPage)
    }

    fun toggleVisibility(isVisible: Boolean) {
        TransitionManager.beginDelayedTransition(parentConstraintLayout, transition)
        (if (isVisible) baseConstraintSet else collapsedConstraintSet).applyTo(parentConstraintLayout)
    }

    fun toggleOpenWithVisibility(isVisible: Boolean) {
        binding.openWithButton.isVisible = isVisible
    }

    fun toggleEditVisibility(isVisible: Boolean) {
        binding.editButton.isVisible = isVisible
    }

    private fun getTopOffset(bottomSheetView: View): Int {
        return if (rootView.height < bottomSheetView.height) max(top, rootView.height - bottomSheetView.height) else 0
    }

    private fun getDefaultPeekHeight(windowInsets: Insets): Int {
        val typedArray = context.theme.obtainStyledAttributes(
            R.style.BottomSheetStyle, intArrayOf(R.attr.behavior_peekHeight)
        )
        val peekHeight = typedArray.getDimensionPixelSize(0, 0)
        typedArray.recycle()
        return peekHeight + windowInsets.bottom
    }
}
