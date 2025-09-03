/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.drive.databinding.ViewUrlDisplayBinding
import com.infomaniak.drive.utils.shareText

class UrlDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewUrlDisplayBinding.inflate(LayoutInflater.from(context), this, true) }

    init {
        binding.apply {
            shareUrlButton.setOnClickListener { copyButton ->
                copyButton.requestFocus()
                shareUrl(context)
            }
            cardViewUrlValue.setOnClickListener {
                shareUrl(context)
            }
        }
    }

    private fun shareUrl(context: Context) {
        context.shareText(text = binding.urlValue.text.toString())
    }

    fun setUrl(url: String) {
        binding.urlValue.text = url
    }
}
