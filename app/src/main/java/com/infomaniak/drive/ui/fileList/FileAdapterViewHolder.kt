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
package com.infomaniak.drive.ui.fileList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.infomaniak.core.recyclerview.CoroutineScopeViewHolder
import com.infomaniak.drive.databinding.CardviewFileGridBinding
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.CardviewFolderGridBinding
import com.infomaniak.drive.views.ProgressLayoutView

sealed class FileViewHolder(open val binding: ViewBinding) : CoroutineScopeViewHolder<View>(binding.root)

class FileLoaderViewHolder(override val binding: ViewBinding) : FileViewHolder(binding)

sealed class FileItemViewHolder(override val binding: ViewBinding) : FileViewHolder(binding) {

    abstract val cardView: MaterialCardView
    abstract val disabledView: View
    abstract val progressLayoutView: ProgressLayoutView
    abstract val fileChecked: MaterialCheckBox
    abstract val filePreview: ImageView
    abstract val menuButton: MaterialButton
    open val fileDate: TextView? = null
    open val stopUploadButton: MaterialButton? = null

    class FileListViewHolder private constructor(
        override val binding: CardviewFileListBinding,
    ) : FileItemViewHolder(binding) {

        override val cardView = binding.fileCardView
        override val disabledView = binding.disabled
        override val progressLayoutView = binding.itemViewFile.progressLayout
        override val fileChecked = binding.itemViewFile.fileChecked
        override val filePreview = binding.itemViewFile.filePreview
        override val menuButton = binding.itemViewFile.menuButton
        override val fileDate = binding.itemViewFile.fileDate
        override val stopUploadButton = binding.itemViewFile.stopUploadButton

        constructor(parent: ViewGroup) : this(
            CardviewFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    class FileGridViewHolder private constructor(
        override val binding: CardviewFileGridBinding,
    ) : FileItemViewHolder(binding) {

        override val cardView = binding.fileCardView
        override val disabledView = binding.disabled
        override val progressLayoutView = binding.progressLayout
        override val fileChecked = binding.fileChecked
        override val filePreview = binding.filePreview
        override val menuButton = binding.menuButton

        constructor(parent: ViewGroup) : this(
            CardviewFileGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    class FolderGridViewHolder private constructor(
        override val binding: CardviewFolderGridBinding,
    ) : FileItemViewHolder(binding) {

        override val cardView = binding.fileCardView
        override val disabledView = binding.disabled
        override val progressLayoutView = binding.progressLayout
        override val fileChecked = binding.fileChecked
        override val filePreview = binding.filePreview
        override val menuButton = binding.menuButton

        constructor(parent: ViewGroup) : this(
            CardviewFolderGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
}
