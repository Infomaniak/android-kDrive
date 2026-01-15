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
import com.infomaniak.core.legacy.utils.getAttributes
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewItemSyncFilesBottomSheetBinding

class ItemSyncFilesBottomSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewItemSyncFilesBottomSheetBinding.inflate(LayoutInflater.from(context), this, true) }

    var textTitle: CharSequence? by binding.title::text
    var textDescription: CharSequence? by binding.description::text
    var isInactive: Boolean by binding.itemSelectActiveIcon::isInvisible

    init {
        attrs?.getAttributes(context, R.styleable.BottomSheetItemView) {
            getString(R.styleable.BottomSheetItemView_text)?.let { textTitle = it }
            getString(R.styleable.BottomSheetItemView_descText)?.let { textDescription = it }
        }
    }
}
