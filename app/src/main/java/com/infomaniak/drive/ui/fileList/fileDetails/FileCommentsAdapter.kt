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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.format.DateUtils.FORMAT_ABBREV_ALL
import android.text.format.DateUtils.getRelativeDateTimeString
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.views.LoaderAdapter
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.databinding.ItemFileCommentBinding
import com.infomaniak.drive.utils.loadAvatar

class FileCommentsAdapter(val onLikeButtonClicked: (currentComment: FileComment) -> Unit) : LoaderAdapter<FileComment>() {

    var onEditClicked: ((comment: FileComment) -> Unit)? = null
    var onDeleteClicked: ((comment: FileComment) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileCommentsViewHolder {
        return FileCommentsViewHolder(ItemFileCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with((holder as FileCommentsViewHolder).binding) {
        if (getItemViewType(position) == VIEW_TYPE_LOADING) {
            commentUserName.resetLoader()
            commentValue.resetLoader()
            commentDateValue.resetLoader()
            return
        }

        val currentComment = itemList[position]

        commentUserAvatar.loadAvatar(currentComment.user)
        commentUserName.text = currentComment.user.displayName
        commentValue.text = currentComment.body
        commentDateValue.text = getRelativeDateTimeString(
            context,
            currentComment.createdAt.time,
            DAY_IN_MILLIS,
            2 * DAY_IN_MILLIS,
            FORMAT_ABBREV_ALL
        )
        likeButton.apply {
            text = currentComment.likesCount.toString()
            setOnClickListener { onLikeButtonClicked(currentComment) }
            setIconTintResource(if (currentComment.liked) R.color.primary else R.color.iconColor)
            TooltipCompat.setTooltipText(this, currentComment.likes?.joinToString(separator = "\n") { it.displayName })
        }
        editButton.setOnClickListener {
            onEditClicked?.invoke(currentComment)
        }

        deleteButton.setOnClickListener {
            onDeleteClicked?.invoke(currentComment)
        }
    }

    fun updateComment(comment: FileComment) {
        getCommentIndex(comment).let { index ->
            itemList[index] = comment
            notifyItemChanged(index)
        }
    }

    fun deleteComment(comment: FileComment) {
        getCommentIndex(comment).let { index ->
            itemList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun addComment(comment: FileComment) {
        itemList.add(comment)
        notifyItemInserted(itemCount - 1)
    }

    private fun getCommentIndex(comment: FileComment): Int {
        return itemList.indexOf(itemList.find { it.id == comment.id })
    }

    class FileCommentsViewHolder(val binding: ItemFileCommentBinding) : ViewHolder(binding.root)
}
