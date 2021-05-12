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
package com.infomaniak.drive.ui.fileList.fileShare

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.utils.loadUrl
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_shareable_item.view.*

class SharedItemsAdapter(
    private val file: File,
    private val onItemClicked: (item: Shareable) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var itemList: ArrayList<Shareable> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_shareable_item, parent, false)
        )
    }

    override fun getItemCount() = itemList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]
        holder.itemView.apply {
            if (item.id != file.createdBy && file.rights?.share == true) {
                setOnClickListener { onItemClicked(item) }
                chevron.visibility = VISIBLE
            } else {
                chevron.visibility = INVISIBLE
            }

            when (item) {
                is DriveUser -> bindDriveUser(item)
                is Invitation -> bindInvitation(item)
                is Tag -> bindTag(item)
            }
        }
    }

    private fun View.bindDriveUser(driveUser: DriveUser) {
        name.text = driveUser.displayName
        infos.text = driveUser.email
        avatar.loadAvatar(driveUser)

        rightsValue.setText(driveUser.getFilePermission().translation)
        if (file.createdBy == driveUser.id) {
            rightsValue.setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
        }
        pendingInvitation.visibility = if (driveUser.status == "pending") VISIBLE else GONE
    }

    private fun View.bindInvitation(invitation: Invitation) {
        if (invitation.displayName.isNullOrEmpty()) {
            name.text = invitation.email
            infos.visibility = GONE
        } else {
            name.text = invitation.displayName
            infos.text = invitation.email
            infos.visibility = VISIBLE
        }

        avatar.apply {
            loadUrl(null, R.drawable.ic_circle_send)
        }

        rightsValue.setText(invitation.getFilePermission().translation)
        if (invitation.id != file.createdBy && file.rights?.share == true) {
            setOnClickListener { onItemClicked(invitation) }
        }
        pendingInvitation.visibility = VISIBLE
    }

    private fun View.bindTag(tag: Tag) {
        if (tag.isAllDriveUsersTag()) {
            name.setText(R.string.allAllDriveUsers)
            avatar.load(R.drawable.ic_circle_drive)
            avatar.setBackgroundColor(Color.parseColor(AccountUtils.getCurrentDrive()?.preferences?.color))
        } else {
            name.text = tag.name
            avatar.load(R.drawable.ic_circle_tag)
            avatar.setBackgroundColor(tag.getColor())
        }

        infos.visibility = GONE
        rightsValue.setText(tag.getFilePermission().translation)
        if (file.createdBy == tag.id) {
            rightsValue.setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
        }
    }

    fun setAll(newItemList: ArrayList<Shareable>) {
        itemList = newItemList
        // Put file creator in first place
        itemList.find { it.id == file.createdBy && it is DriveUser }?.let { item ->
            itemList.removeAt(itemList.indexOf(item))
            itemList.add(0, item)
        }

        notifyDataSetChanged()
    }

    fun putAll(itemList: ArrayList<Shareable>) {
        itemList.forEach { newShareable ->
            this.itemList.find { it.id == newShareable.id }?.let {
                val index = getIndexOfShareable(newShareable.id)
                this.itemList[index] = it
            } ?: run {
                this.itemList.add(newShareable)
            }
        }
        notifyDataSetChanged()
    }

    fun removeItem(item: Shareable) {
        val index = getIndexOfShareable(item.id)
        itemList.removeAt(index)
        notifyItemRemoved(index)
    }

    fun updateItemPermission(shareable: Shareable, newPermission: Shareable.ShareablePermission) {
        val index = getIndexOfShareable(shareable.id)
        itemList.getOrNull(index)?.apply {
            permission = newPermission.apiValue
            notifyItemChanged(index)
        }
    }

    private fun getIndexOfShareable(id: Int) = itemList.indexOfFirst { it.id == id }
}