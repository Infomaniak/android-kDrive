/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.core.legacy.utils.getAttributes
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.ViewProgressLayoutBinding

class ProgressLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ViewProgressLayoutBinding.inflate(LayoutInflater.from(context), this, true) }

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.ProgressLayoutView) {
                val indicatorSize = getDimensionPixelSize(R.styleable.ProgressLayoutView_progressLayoutIndicatorSize, 12.toPx())
                fileOffline.layoutParams = LayoutParams(indicatorSize, indicatorSize)
                fileOfflineProgression.indicatorSize = indicatorSize
            }
        }
    }

    fun setIndeterminateProgress() = with(binding) {
        fileOffline.isGone = true
        fileOfflineProgression.apply {
            isGone = true // We need to hide the view before updating its `isIndeterminate`
            isIndeterminate = true
            isVisible = true
        }
    }

    fun setProgress(file: File) = with(binding) {
        fileOffline.isGone = true
        fileOfflineProgression.apply {
            if (isIndeterminate) {
                isGone = true // We need to hide the view before updating its `isIndeterminate`
                isIndeterminate = false
            }
            isVisible = true
            progress = file.currentProgress
        }
    }

    fun hideProgress() = with(binding) {
        fileOffline.isVisible = true
        fileOfflineProgression.isGone = true
    }
}
