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
package com.infomaniak.drive.ui.menu.settings

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewItemSettingBinding
import com.infomaniak.lib.core.utils.getAttributes

class ItemSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewItemSettingBinding.inflate(LayoutInflater.from(context), this, true) }

    private var action: Action = Action.None

    var isChecked
        get() = binding.toggle.isChecked
        set(value) {
            binding.toggle.isChecked = value
        }

    var title: CharSequence by binding.title::text
    var endText: CharSequence by binding.endText::text

    init {
        attrs?.getAttributes(context, R.styleable.ItemSettingView) {
            action = Action.entries[getInteger(R.styleable.ItemSettingView_itemAction, 0)]
            title = getString(R.styleable.ItemSettingView_title) ?: ""
            endText = getString(R.styleable.ItemSettingView_endText) ?: ""
            setDescription(getString(R.styleable.ItemSettingView_description) ?: "")

            getDrawable(R.styleable.ItemSettingView_icon).let {
                binding.icon.setImageDrawable(it)
                binding.icon.isGone = it == null
            }

            setIconEndVisibility()

        }
    }

    override fun setOnClickListener(listener: OnClickListener?) = with(binding) {
        root.setOnClickListener { listener?.onClick(root) }
    }

    fun setDescription(description: String) {
        binding.description.apply {
            text = description
            isGone = description.isBlank()
        }
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        binding.toggle.setOnCheckedChangeListener(listener)
    }

    fun setIconColor(@ColorInt color: Int) {
        binding.icon.imageTintList = ColorStateList.valueOf(color)
    }

    fun setIconEndVisibility() {
        binding.chevron.isVisible = action == Action.Chevron
        binding.toggle.isVisible = action == Action.Toggle
        binding.endText.isVisible = action == Action.Text
    }

    private enum class Action {
        None,
        Chevron,
        Toggle,
        Text,
    }
}
