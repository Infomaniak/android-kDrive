/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.ViewExternalFileInfoActionsBinding
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener

class ExternalFileInfoActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewExternalFileInfoActionsBinding.inflate(LayoutInflater.from(context), this, true) }

    private lateinit var onItemClickListener: OnItemClickListener

    fun setClickListener(onItemClickListener: OnItemClickListener) {
        this.onItemClickListener = onItemClickListener
        initOnClickListeners()
    }

    fun updateWithExternalFile(file: File) {
        binding.fileView.setFileItem(file)
    }

    private fun initOnClickListeners() = with(binding) {
        openWith.setOnClickListener { onItemClickListener.openWithClicked() }
        shareFile.setOnClickListener { onItemClickListener.shareFile() }
        saveToKDrive.setOnClickListener { onItemClickListener.saveToKDriveClicked() }
        print.setOnClickListener { onItemClickListener.printClicked() }
    }
}
