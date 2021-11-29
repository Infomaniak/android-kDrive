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

import android.text.format.DateUtils.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.RequestManager
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.utils.loadGlide
import com.infomaniak.drive.utils.loadGlideUrl
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.LoaderCardView
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_home_file_activity.view.*
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import java.util.*

class LastActivitiesAdapter(private val glideRequestManager: RequestManager) : LoaderAdapter<FileActivity>() {

    var isComplete = false
    var onFileClicked: ((currentFile: File, validPreviewFiles: ArrayList<File>) -> Unit)? = null
    var onMoreFilesClicked: ((fileActivity: FileActivity, validPreviewFiles: ArrayList<File>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_home_file_activity, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.apply {
            if (getItemViewType(position) == VIEW_TYPE_LOADING) {
                (this as LoaderCardView).startLoading()
            } else {
                (this as LoaderCardView).stopLoading()
                val fileActivity = itemList[position]
                fileActivity.user?.let { user -> this.createActivity(fileActivity, user) }
            }
        }
    }

    private fun View.createActivity(fileActivity: FileActivity, user: DriveUser) {
        val fileActivityName: CharSequence = fileActivity.file?.name ?: fileActivity.path.substringAfterLast("/")
        val sizeMergedFile = fileActivity.mergedFileActivities.size
        actionValue.text = resources.getQuantityString(fileActivity.homeTranslation, sizeMergedFile + 1, sizeMergedFile + 1)
        userAvatar.loadAvatar(user)
        userName.text = user.displayName
        dateValue.text = getRelativeDateTimeString(
            context,
            fileActivity.createdAt.time,
            DAY_IN_MILLIS,
            2 * DAY_IN_MILLIS, FORMAT_ABBREV_ALL
        )

        fileIcon.loadGlide(getFileTypeIcon(fileActivity.file))
        fileName1.text = fileActivityName

        if (sizeMergedFile >= 1) {
            cardFilePreview2.isVisible = true
            fileIcon2.isVisible = true
            fileName2.isVisible = true

            fileActivity.file.loadPreview(filePreview1, filePreviewIcon1 as ConstraintLayout)
            val file2 = fileActivity.mergedFileActivities[0].file
            file2.loadPreview(filePreview2, filePreviewIcon2 as ConstraintLayout)
            fileIcon2.loadGlide(getFileTypeIcon(file2))
            fileName2.text = file2?.name ?: fileActivity.mergedFileActivities[0].path.substringAfterLast("/")

            if (sizeMergedFile == 1) {
                cardFilePreview3.isGone = true
                fileIcon3.isGone = true
                fileName3.isGone = true
                (cardFilePreview1.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "3:2"
                (cardFilePreview2.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "3:2"
            } else {
                cardFilePreview3.isVisible = true
                fileIcon3.isVisible = true
                fileName3.isVisible = true

                val file3 = fileActivity.mergedFileActivities[1].file
                file3.loadPreview(filePreview3, filePreviewIcon3 as ConstraintLayout)
                (cardFilePreview1.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1:1"
                (cardFilePreview2.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1:1"

                if (sizeMergedFile > 2) {
                    fileIcon3.loadGlide(R.drawable.ic_copy)
                    fileName3.text = fileName3.context.getString(R.string.fileActivityOtherFiles, sizeMergedFile - 1)
                    moreFile.text = "+${sizeMergedFile - 1}"
                    moreFile.isVisible = true
                } else {
                    fileIcon3.loadGlide(getFileTypeIcon(file3))
                    fileName3.text = file3?.name ?: fileActivity.mergedFileActivities[1].path.substringAfterLast("/")
                    moreFile.isGone = true
                }
            }
        } else {
            cardFilePreview2.isGone = true
            cardFilePreview3.isGone = true
            fileIcon2.isGone = true
            fileIcon3.isGone = true
            fileName2.isGone = true
            fileName3.isGone = true
            (cardFilePreview1.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "3:1"
            fileActivity.file.loadPreview(filePreview1, filePreviewIcon1 as ConstraintLayout)
        }

        fileActivity.file?.let { file ->
            val validPreviewFiles = arrayListOf(file).apply { addAll(fileActivity.mergedFileActivities.map { it.file!! }) }

            arrayListOf(cardFilePreview1, fileName1).setOnClick(file, validPreviewFiles)

            arrayListOf(cardFilePreview2, fileName2).setOnClick(
                fileActivity.mergedFileActivities.firstOrNull()?.file,
                validPreviewFiles
            )

            val file3 = fileActivity.mergedFileActivities.getOrNull(1)?.file
            arrayListOf(cardFilePreview3, fileName3).setOnClick(
                file3,
                validPreviewFiles
            )
            if (file3?.isFolder() == false && fileActivity.mergedFileActivities.size > 2) {
                fileName3.setOnClickListener { onMoreFilesClicked?.invoke(fileActivity, validPreviewFiles) }
            }

        } ?: run {
            cardFilePreview1.isClickable = false
            cardFilePreview2.isClickable = false
            cardFilePreview3.isClickable = false
            fileName1.isClickable = false
            fileName2.isClickable = false
            fileName3.isClickable = false
        }
    }

    private fun List<View>.setOnClick(file: File?, validPreviewFiles: ArrayList<File>) = forEach {
        if (file == null || file.isFolder()) {
            it.isClickable = false
        } else {
            it.setOnClickListener { onFileClicked?.invoke(file, validPreviewFiles) }
        }
    }

    private fun File?.loadPreview(imageView: ImageView, iconView: ConstraintLayout) {
        if (this?.hasThumbnail == true && getFileType() == File.ConvertedType.IMAGE || this?.getFileType() == File.ConvertedType.VIDEO) {
            iconView.isGone = true
            imageView.isVisible = true
            imageView.loadGlideUrl(glideRequestManager, thumbnail(), getFileType().icon)
        } else {
            imageView.isGone = true
            iconView.isVisible = true
            iconView.icon.loadGlide(getFileTypeIcon(this))
        }
    }

    private fun getFileTypeIcon(file: File?) = file?.getFileType()?.icon ?: R.drawable.ic_file
}
