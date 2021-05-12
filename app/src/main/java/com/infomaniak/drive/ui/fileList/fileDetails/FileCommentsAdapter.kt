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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.text.format.DateUtils.*
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.views.PaginationAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_file_comment.view.*

class FileCommentsAdapter(
    override val itemList: ArrayList<FileComment> = arrayListOf(),
    val onLikeButtonClicked: (currentComment: FileComment) -> Unit
) : PaginationAdapter<FileComment>() {

    var onEditClicked: ((comment: FileComment) -> Unit)? = null
    var onDeleteClicked: ((comment: FileComment) -> Unit)? = null

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_file_comment, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentComment = itemList[position]
        holder.itemView.apply {
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
            likeButton.text = currentComment.likesCount.toString()
            likeButton.setOnClickListener {
                onLikeButtonClicked(currentComment)
            }
            likeButton.setIconTintResource(if (currentComment.liked) R.color.primary else R.color.iconColor)
            TooltipCompat.setTooltipText(
                likeButton,
                currentComment.likes?.joinToString(separator = "\n") { it.displayName.toString() })

            editButton.setOnClickListener {
                onEditClicked?.invoke(currentComment)
            }

            deleteButton.setOnClickListener {
                onDeleteClicked?.invoke(currentComment)
            }
        }
    }

    private fun getCommentIndex(comment: FileComment): Int {
        return itemList.indexOf(itemList.find { it.id == comment.id })
    }

    override fun getItemCount() = itemList.size
}