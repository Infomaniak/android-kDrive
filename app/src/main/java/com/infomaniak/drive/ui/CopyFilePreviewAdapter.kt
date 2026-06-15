/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.core.ui.view.extension.setMargins
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.ui.fileList.FileAdapter.Companion.setCorners
import com.infomaniak.drive.utils.setFileItemWithoutCategories

class CopyFilePreviewAdapter(private val files: List<File>) : Adapter<CopyFilePreviewAdapter.CopyFilePreviewViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CopyFilePreviewViewHolder {
        return CopyFilePreviewViewHolder(CardviewFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: CopyFilePreviewViewHolder, position: Int): Unit = with(holder.binding) {
        itemViewFile.setFileItemWithoutCategories(files[position])
        itemViewFile.fileSize.isGone = true
        itemViewFile.fileDate.isGone = true
        itemViewFile.menuButton.isGone = true
        fileCardView.setMargins(left = 0, right = 0)
        fileCardView.setCorners(position, itemCount)
    }

    override fun getItemCount() = files.size

    class CopyFilePreviewViewHolder(val binding: CardviewFileListBinding) : ViewHolder(binding.root)
}
