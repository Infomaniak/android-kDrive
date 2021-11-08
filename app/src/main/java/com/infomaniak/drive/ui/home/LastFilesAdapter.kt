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
package com.infomaniak.drive.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT
import androidx.core.view.isGone
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setMargin
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_file_grid.view.*

class LastFilesAdapter : LoaderAdapter<File>() {

    var onFileClicked: ((file: File) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_file_grid, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.apply {
            val marginStandardSmall = resources.getDimensionPixelSize(R.dimen.marginStandardSmall)
            fileCardView.setMargin(2, marginStandardSmall, marginStandardSmall, marginStandardSmall)
            menuButton.isGone = true

            if (getItemViewType(position) == VIEW_TYPE_LOADING) {
                fileCardView.startLoading()
                fileNameLayout.layoutParams.width = 80.toPx()
            } else {
                val file = itemList[position]
                fileCardView.stopLoading()
                fileNameLayout.layoutParams.width = WRAP_CONTENT
                setFileItem(file, isGrid = true)
                fileCardView.setOnClickListener { onFileClicked?.invoke(file) }
            }
        }
    }
}

