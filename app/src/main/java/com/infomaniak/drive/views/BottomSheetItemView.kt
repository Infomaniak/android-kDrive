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
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewBottomSheetItemBinding
import com.infomaniak.lib.core.utils.getAttributes

class BottomSheetItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewBottomSheetItemBinding.inflate(LayoutInflater.from(context), this, true) }

    var icon: Drawable?
        get() = binding.icon.drawable
        set(value) {
            binding.icon.apply {
                setImageDrawable(value)
                isGone = value == null
            }
        }

    var text: CharSequence?
        get() = binding.text.text
        set(value) {
            binding.text.text = value
        }

    var iconTintList: ColorStateList?
        get() = binding.icon.imageTintList
        set(value) {
            binding.icon.imageTintList = value
        }

    var shouldShowMyKSuiteChip: Boolean
        get() = binding.myKSuitePlusChip.isVisible
        set(value) {
            binding.myKSuitePlusChip.isVisible = value
        }

    var shouldShowKSuiteProChip: Boolean
        get() = binding.kSuiteProChip.isVisible
        set(value) {
            binding.kSuiteProChip.isVisible = value
        }

    override fun setEnabled(enabled: Boolean) {
        binding.disabledOverlay.isGone = enabled
    }

    override fun isEnabled(): Boolean = binding.disabledOverlay.isGone

    init {
        attrs?.getAttributes(context, R.styleable.BottomSheetItemView) {
            icon = getDrawable(R.styleable.BottomSheetItemView_icon)
            getString(R.styleable.BottomSheetItemView_text)?.let { text = it }
            getColorStateList(R.styleable.BottomSheetItemView_iconTint)?.let { iconTintList = it }
        }
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) = binding.root.setOnClickListener(onClickListener)
}
