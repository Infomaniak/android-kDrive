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
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ViewNoItemsBinding

class NoItemsLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    lateinit var iNoItemsLayoutView: INoItemsLayoutView

    var description: Int? = null
    var viewsToDisable: List<View>? = null
    var onNetworkUnavailableRefresh: (() -> Unit)? = null

    private val binding by lazy { ViewNoItemsBinding.inflate(LayoutInflater.from(context), this, true) }

    fun enableSecondaryBackground() {
        binding.noItemsIconLayout.root.setBackgroundResource(R.drawable.round_empty_secondary)
    }

    fun toggleVisibility(isVisible: Boolean, noNetwork: Boolean = false, showRefreshButton: Boolean = true) = with(binding) {

        if (isVisible) {
            this@NoItemsLayoutView.isVisible = true
            viewsToDisable?.forEach { it.isEnabled = false }
            iNoItemsLayoutView.noItemsInitialListView.isGone = true

            if (noNetwork) {
                noItemsIconLayout.icon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_no_network))
                noItemsTitle.setText(R.string.noFilesDescriptionNoNetwork)
                if (showRefreshButton) {
                    noItemsRefreshButton.isVisible = true
                    noItemsRefreshButton.setOnClickListener { onNetworkUnavailableRefresh?.invoke() }
                }
            } else {
                noItemsIconLayout.icon.setImageResource(iNoItemsLayoutView.noItemsIcon)
                noItemsIconLayout.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.iconColor))
                noItemsTitle.setText(iNoItemsLayoutView.noItemsTitle)
                noItemsRefreshButton.isGone = true
            }

            description?.let {
                noItemsDescription.isVisible = true
                noItemsDescription.text = context.getString(it)
            } ?: run {
                noItemsDescription.isGone = true
            }
        } else {
            this@NoItemsLayoutView.isGone = true
            viewsToDisable?.forEach { it.isEnabled = true }
            iNoItemsLayoutView.noItemsInitialListView.isVisible = true
        }
    }

    interface INoItemsLayoutView {
        val noItemsIcon: Int
        val noItemsTitle: Int
        val noItemsInitialListView: View
    }
}
