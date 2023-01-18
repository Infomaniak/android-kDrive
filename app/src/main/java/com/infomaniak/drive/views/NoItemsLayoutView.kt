/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.android.synthetic.main.view_no_items.view.*

class NoItemsLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    lateinit var iNoItemsLayoutView: INoItemsLayoutView

    var description: Int? = null
    var viewsToDisable: List<View>? = null
    var onNetworkUnavailableRefresh: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_no_items, this)
    }

    fun enableSecondaryBackground() {
        noItemsIconLayout.setBackgroundResource(R.drawable.round_empty_secondary)
    }

    fun toggleVisibility(isVisible: Boolean, noNetwork: Boolean = false, showRefreshButton: Boolean = true) {

        if (isVisible) {
            this.isVisible = true
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
            this.isGone = true
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
