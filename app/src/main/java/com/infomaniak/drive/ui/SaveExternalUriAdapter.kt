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
package com.infomaniak.drive.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.ui.SaveExternalUriAdapter.SaveExternalUriViewHolder
import com.infomaniak.drive.ui.fileList.FileAdapter.Companion.setCorners
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItemWithoutCategories

class SaveExternalUriAdapter(val uris: MutableList<Pair<Uri, String>>) : Adapter<SaveExternalUriViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaveExternalUriViewHolder {
        return SaveExternalUriViewHolder(CardviewFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SaveExternalUriViewHolder, position: Int): Unit = with(holder.binding) {
        val (uri, name) = uris[position]

        val file = File(
            id = uri.hashCode(),
            name = name,
            path = uri.toString(),
            isFromUploads = true,
        )

        itemViewFile.setFileItemWithoutCategories(file)
        root.apply {
            initView(position)
            setOnClickListener { onItemClicked(file, position) }
        }
    }

    override fun getItemCount() = uris.size

    private fun updateFileName(position: Int, newName: String) {
        uris[position] = uris[position].first to newName
        notifyItemChanged(position)
    }

    private fun CardviewFileListBinding.initView(position: Int) {
        itemViewFile.apply {
            fileSize.isGone = true
            fileDate.isGone = true
        }

        fileCardView.setMargins(left = 0, right = 0)
        fileCardView.setCorners(position, itemCount)

        itemViewFile.menuButton.apply {
            isVisible = true
            isEnabled = false
            isClickable = false
            setIconResource(R.drawable.ic_edit)
        }
    }

    private fun CardviewFileListBinding.onItemClicked(file: File, position: Int) {
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

    class SaveExternalUriViewHolder(val binding: CardviewFileListBinding) : ViewHolder(binding.root)
}
