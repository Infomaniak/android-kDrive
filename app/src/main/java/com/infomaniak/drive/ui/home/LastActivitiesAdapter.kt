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
package com.infomaniak.drive.ui.home

import android.text.format.DateUtils.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.databinding.CardviewHomeFileActivityBinding
import com.infomaniak.drive.databinding.EmptyIconLayoutBinding
import com.infomaniak.drive.databinding.ItemLastActivitiesSubtitleBinding
import com.infomaniak.drive.utils.loadAny
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.views.LoaderAdapter

class LastActivitiesAdapter : LoaderAdapter<FileActivity>() {

    var onFileClicked: ((currentFile: File, validPreviewFiles: ArrayList<File>) -> Unit)? = null
    var onMoreFilesClicked: ((fileActivity: FileActivity, validPreviewFiles: ArrayList<File>) -> Unit)? = null

    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun getItemViewType(position: Int): Int = if (position == 0) VIEW_TYPE_SUBTITLE else super.getItemViewType(position-1)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LastActivitiesViewHolder {
        return when (viewType) {
            VIEW_TYPE_SUBTITLE -> SubtitleViewHolder(
                ItemLastActivitiesSubtitleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> ActivitiesViewHolder(
                CardviewHomeFileActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is ActivitiesViewHolder) holder.binding.bindActivity(position)
    }

    private fun CardviewHomeFileActivityBinding.bindActivity(position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_LOADING) {
            root.startLoading()
            userName.resetLoader()
            actionValue.resetLoader()
            dateValue.resetLoader()
        } else {
            root.stopLoading()
            val fileActivity = itemList[position-1]
            fileActivity.user?.let { user -> createActivity(fileActivity, user) }
        }
    }

    private fun CardviewHomeFileActivityBinding.createActivity(fileActivity: FileActivity, user: DriveUser) {
        val fileActivityName: CharSequence = fileActivity.file?.name ?: fileActivity.newPath.substringAfterLast("/")
        val sizeMergedFile = fileActivity.mergedFileActivities.size
        actionValue.text =
            context.resources.getQuantityString(fileActivity.homeTranslation, sizeMergedFile + 1, sizeMergedFile + 1)
        userAvatar.loadAvatar(user)
        userName.text = user.displayName
        dateValue.text = getRelativeDateTimeString(
            context,
            fileActivity.createdAt.time,
            DAY_IN_MILLIS,
            2 * DAY_IN_MILLIS, FORMAT_ABBREV_ALL
        )

        fileIcon.load(getFileTypeIcon(fileActivity.file))
        fileName1.text = fileActivityName

        if (sizeMergedFile >= 1) {
            cardFilePreview2.isVisible = true
            fileIcon2.isVisible = true
            fileName2.isVisible = true

            fileActivity.file.loadPreview(filePreview1, filePreviewIcon1)
            val file2 = fileActivity.mergedFileActivities[0].file
            file2.loadPreview(filePreview2, filePreviewIcon2)
            fileIcon2.load(getFileTypeIcon(file2))
            fileName2.text = file2?.name ?: fileActivity.mergedFileActivities[0].newPath.substringAfterLast("/")

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
                file3.loadPreview(filePreview3, filePreviewIcon3)
                (cardFilePreview1.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1:1"
                (cardFilePreview2.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1:1"

                if (sizeMergedFile > 2) {
                    fileIcon3.load(R.drawable.ic_copy)
                    fileName3.text = fileName3.context.getString(R.string.fileActivityOtherFiles, sizeMergedFile - 1)
                    moreFile.text = "+${sizeMergedFile - 1}"
                    moreFile.isVisible = true
                } else {
                    fileIcon3.load(getFileTypeIcon(file3))
                    fileName3.text = file3?.name ?: fileActivity.mergedFileActivities[1].newPath.substringAfterLast("/")
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
            fileActivity.file.loadPreview(filePreview1, filePreviewIcon1)
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
        if (file == null) {
            it.isClickable = false
        } else {
            it.setOnClickListener { onFileClicked?.invoke(file, validPreviewFiles) }
        }
    }

    private fun File?.loadPreview(imageView: ImageView, iconViewBinding: EmptyIconLayoutBinding) {
        if (this?.hasThumbnail == true && getFileType() == ExtensionType.IMAGE || this?.getFileType() == ExtensionType.VIDEO) {
            iconViewBinding.root.isGone = true
            imageView.isVisible = true
            imageView.loadAny(ApiRoutes.getThumbnailUrl(file = this), getFileType().icon)
        } else {
            imageView.isGone = true
            iconViewBinding.root.isVisible = true
            iconViewBinding.icon.load(getFileTypeIcon(this))
        }
    }

    private fun getFileTypeIcon(file: File?) = file?.getFileType()?.icon ?: R.drawable.ic_file

    open class LastActivitiesViewHolder(open val binding: ViewBinding) : ViewHolder(binding.root)
    class SubtitleViewHolder(override val binding: ItemLastActivitiesSubtitleBinding) : LastActivitiesViewHolder(binding)
    class ActivitiesViewHolder(override val binding: CardviewHomeFileActivityBinding) : LastActivitiesViewHolder(binding)

    companion object {
        private const val VIEW_TYPE_SUBTITLE = 3
    }
}
