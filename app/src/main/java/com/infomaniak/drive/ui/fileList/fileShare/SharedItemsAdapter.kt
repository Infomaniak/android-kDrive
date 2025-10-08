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
package com.infomaniak.drive.ui.fileList.fileShare

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Share.UserFileAccess
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.databinding.ItemShareableItemBinding
import com.infomaniak.drive.ui.fileList.fileShare.SharedItemsAdapter.SharedItemsViewHolder
import com.infomaniak.drive.utils.loadAny
import com.infomaniak.drive.utils.loadAvatar

class SharedItemsAdapter(
    private val file: File,
    private val onItemClicked: (item: Shareable) -> Unit,
) : Adapter<SharedItemsViewHolder>() {

    private var itemList: ArrayList<Shareable> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedItemsViewHolder {
        return SharedItemsViewHolder(ItemShareableItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = itemList.size

    override fun onBindViewHolder(holder: SharedItemsViewHolder, position: Int) = with(holder.binding) {
        val item = itemList[position]
        if (item.id != file.createdBy && file.rights?.canShare == true) {
            root.setOnClickListener { onItemClicked(item) }
            chevron.isVisible = true
        } else {
            chevron.isInvisible = true
        }

        when (item) {
            is UserFileAccess -> bindDriveUser(item)
            is Invitation -> bindInvitation(item)
            is Team -> bindTeam(item)
        }
    }

    private fun ItemShareableItemBinding.bindDriveUser(userFileAccess: UserFileAccess) {
        name.text = userFileAccess.name
        infos.text = userFileAccess.email
        userFileAccess.user?.let { avatar.loadAvatar(it) }

        rightsValue.setText(userFileAccess.getFilePermission().translation)

        externalUserLabel.apply {
            if (userFileAccess.isExternalUser) {
                text = context.getString(
                    if (userFileAccess.status == UserFileAccess.UserFileAccessStatus.PENDING) R.string.shareUserNotAccepted
                    else R.string.shareUserExternal
                )
                isVisible = true
            } else isGone = true
        }

        if (file.createdBy == userFileAccess.id) {
            rightsValue.setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
        }
    }

    private fun ItemShareableItemBinding.bindInvitation(invitation: Invitation) {
        if (invitation.name.isEmpty()) {
            name.text = invitation.email
            infos.isGone = true
        } else {
            name.text = invitation.name
            infos.text = invitation.email
            infos.isVisible = true
        }

        avatar.loadAny(null, errorRes = R.drawable.ic_circle_send)

        rightsValue.setText(invitation.getFilePermission().translation)
        externalUserLabel.apply {
            text = context.getString(R.string.shareUserNotAccepted)
            isVisible = true
        }

        if (invitation.id != file.createdBy && file.rights?.canShare == true) {
            root.setOnClickListener { onItemClicked(invitation) }
        }
    }

    private fun ItemShareableItemBinding.bindTeam(team: Team) {
        if (team.isAllUsers()) {
            name.setText(R.string.allAllDriveUsers)
            avatar.load(R.drawable.ic_circle_drive)
        } else {
            name.text = team.name
            avatar.load(R.drawable.ic_circle_team)
        }
        avatar.setBackgroundColor(team.getParsedColor())

        infos.isGone = true
        rightsValue.setText(team.getFilePermission().translation)
        if (file.createdBy == team.id) {
            rightsValue.setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
        }
    }

    fun setAll(newItemList: ArrayList<Shareable>) {
        itemList = newItemList
        // Put file creator in first place
        if (itemList.size > 1) {
            itemList.find { it.id == file.createdBy && it is DriveUser }?.let { item ->
                itemList.removeAt(itemList.indexOf(item))
                itemList.add(0, item)
            }
        }

        notifyItemRangeInserted(0, newItemList.size)
    }

    fun putAll(newItemsList: ArrayList<Shareable>) {
        newItemsList.forEach { newShareable ->
            itemList.find { it.id == newShareable.id }?.let {
                itemList[getIndexOfShareable(newShareable.id)] = it
            } ?: run {
                itemList.add(newShareable)
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
            right = newPermission.apiValue
            notifyItemChanged(index)
        }
    }

    private fun getIndexOfShareable(id: Int) = itemList.indexOfFirst { it.id == id }

    class SharedItemsViewHolder(val binding: ItemShareableItemBinding) : ViewHolder(binding.root)
}
