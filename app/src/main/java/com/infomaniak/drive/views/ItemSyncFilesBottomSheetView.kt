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
package com.infomaniak.drive.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewItemSyncFilesBottomSheetBinding
import com.infomaniak.lib.core.utils.getAttributes

class ItemSyncFilesBottomSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewItemSyncFilesBottomSheetBinding.inflate(LayoutInflater.from(context), this, true) }

    var textTitle: CharSequence?
        get() = binding.title.text
        set(value) {
            binding.title.text = value
        }

    var textDescription: CharSequence?
        get() = binding.description.text
        set(value) {
            binding.description.text = value
        }

    var isActive: Boolean
        get() = binding.itemSelectActiveIcon.isVisible
        set(value) {
            binding.itemSelectActiveIcon.isInvisible = !value
        }

    init {
        attrs?.getAttributes(context, R.styleable.BottomSheetItemView) {
            with(binding) {
                getString(R.styleable.BottomSheetItemView_text)?.let { textTitle = it }
                getString(R.styleable.BottomSheetItemView_descText)?.let { textDescription = it }
            }
        }
    }
}
