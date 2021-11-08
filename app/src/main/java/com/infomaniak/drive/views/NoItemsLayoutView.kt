/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import kotlin.properties.Delegates

class NoItemsLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var initialListView: View
    private var icon by Delegates.notNull<Int>()
    private var title by Delegates.notNull<Int>()
    private var description: Int? = null
    private var onNetworkUnavailableRefresh: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_no_items, this)
    }

    // TODO : AttributeSet ?
    fun setup(
        icon: Int = R.drawable.ic_folder_filled,
        title: Int,
        description: Int? = null,
        initialListView: View,
        secondaryBackground: Boolean = false,
        onNetworkUnavailableRefresh: (() -> Unit)? = null
    ) {
        this.icon = icon
        this.title = title
        this.description = description
        this.initialListView = initialListView
        this.onNetworkUnavailableRefresh = onNetworkUnavailableRefresh
        if (secondaryBackground) noItemsIconLayout.setBackgroundResource(R.drawable.round_empty_secondary)
    }

    fun toggleVisibility(isVisible: Boolean, noNetwork: Boolean = false, showRefreshButton: Boolean = true) {

        if (!isVisible) {
            this.isGone = true
            initialListView.isVisible = true

        } else {

            this.isVisible = true
            initialListView.isGone = true
            noItemsIconLayout.icon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_no_network))
            noItemsTitle.setText(R.string.noFilesDescriptionNoNetwork)

            if (noNetwork) {
                if (showRefreshButton) {
                    noItemsRefreshButton.isVisible = true
                    noItemsRefreshButton.setOnClickListener { onNetworkUnavailableRefresh?.invoke() }
                }
            } else {
                noItemsIconLayout.icon.setImageResource(icon)
                noItemsTitle.setText(title)
                noItemsRefreshButton.isGone = true
            }

            description?.let {
                noItemsDescription.isVisible = true
                noItemsDescription.text = context.getString(it)
            } ?: run {
                noItemsDescription.isGone = true
            }
        }
    }
}