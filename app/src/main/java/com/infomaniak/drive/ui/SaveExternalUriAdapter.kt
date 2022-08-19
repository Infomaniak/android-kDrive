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
package com.infomaniak.drive.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileAdapter.Companion.setCorners
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setMargin
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_file_list.view.*
import kotlinx.android.synthetic.main.item_file.view.*

class SaveExternalUriAdapter(val uris: MutableList<Pair<Uri, String>>) : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.cardview_file_list, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (uri, name) = uris[position]

        with(holder.itemView) {
            val file = File(
                id = uri.hashCode(),
                name = name,
                path = uri.toString(),
                isFromUploads = true
            )

            setFileItem(file)
            initView(position)
            setOnClickListener { onItemClicked(file, position) }
        }
    }

    override fun getItemCount() = uris.size

    private fun updateFileName(position: Int, newName: String) {
        uris[position] = uris[position].first to newName
        notifyItemChanged(position)
    }

    private fun View.initView(position: Int) {
        fileSize.isGone = true
        fileDate.isGone = true

        fileCardView.setMargin(left = 0, right = 0)
        fileCardView.setCorners(position, itemCount)

        menuButton.apply {
            isVisible = true
            isEnabled = false
            isClickable = false
            setIconResource(R.drawable.ic_edit)
        }
    }

    private fun View.onItemClicked(file: File, position: Int) {
        Utils.createPromptNameDialog(
            context = context,
            title = R.string.buttonRename,
            fieldName = R.string.hintInputFileName,
            positiveButton = R.string.buttonSave,
            fieldValue = file.name,
            selectedRange = file.getFileName().count()
        ) { dialog, name ->
            updateFileName(position, name)
            dialog.dismiss()
        }
    }
}