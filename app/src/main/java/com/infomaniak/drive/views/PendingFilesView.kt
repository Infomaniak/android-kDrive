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
import androidx.core.content.res.getStringOrThrow
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.shape.CornerFamily
import com.infomaniak.core.legacy.utils.getAttributes
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.utils.navigateToUploadView

class PendingFilesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val binding by lazy { CardviewFileListBinding.inflate(LayoutInflater.from(context), this, true) }

    private var folderId: Int? = null
    private var fragment: Fragment? = null

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.PendingFilesView) {
                itemViewFile.fileName.text = getStringOrThrow(R.styleable.PendingFilesView_title)
            }

            val radius = context.resources.getDimension(R.dimen.cardViewRadius)
            root.shapeAppearanceModel = root.shapeAppearanceModel.toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                .setTopRightCorner(CornerFamily.ROUNDED, radius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                .setBottomRightCorner(CornerFamily.ROUNDED, radius)
                .build()

            binding.itemViewFile.apply {
                filePreview.isGone = true
                fileProgression.isVisible = true
            }

            root.setOnClickListener { folderId?.let { id -> fragment?.navigateToUploadView(id) } }

            if (isInEditMode) {
                itemViewFile.apply {
                    fileSize.text = context.resources.getQuantityString(R.plurals.uploadInProgressNumberFile, 5, 5)
                    fileSeparator.isGone = true
                    fileDate.text = ""
                    fileFavorite.isGone = true
                    progressLayout.isGone = true
                    endIconLayout.isGone = true
                }
            }
        }
    }

    fun setUploadFileInProgress(fragment: Fragment, folderId: Int) {
        this.fragment = fragment
        this.folderId = folderId
    }

    fun updateUploadFileInProgress(pendingFilesCount: Int) {
        if (pendingFilesCount > 0) {
            binding.itemViewFile.fileSize.text = context.resources.getQuantityString(
                R.plurals.uploadInProgressNumberFile,
                pendingFilesCount,
                pendingFilesCount,
            )
            isVisible = true
        } else {
            isGone = true
        }
    }
}
