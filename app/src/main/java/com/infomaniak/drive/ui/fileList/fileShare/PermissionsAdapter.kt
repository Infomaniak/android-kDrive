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

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File.FolderPermission
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_permission.view.*
import kotlinx.android.synthetic.main.item_user_avatar.view.*

class PermissionsAdapter(
    var selectionPosition: Int = 0,
    private var currentUser: User? = null,
    private var isExternalUser: Boolean = false,
    private var sharedUsers: ArrayList<DriveUser> = ArrayList(),

    private var showSelectionCheckIcon: Boolean = true,
    private var onUpgradeOfferClicked: (() -> Unit)? = null,
    private val onPermissionChanged: (newPermission: Permission) -> Unit,
) : RecyclerView.Adapter<ViewHolder>() {

    var permissionList: ArrayList<Permission> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.cardview_permission, parent, false)
        )
    }

    fun setAll(newPermissions: ArrayList<Permission>) {
        permissionList = newPermissions
        notifyDataSetChanged()
    }

    fun setUsers(users: ArrayList<DriveUser>) {
        sharedUsers = users
    }

    fun addItem(permission: Permission) {
        permissionList.add(permission)
        notifyItemInserted(permissionList.size)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val permission = permissionList[position]
        holder.itemView.apply {
            permissionCard.apply {
                isCheckable = true
                setStrokeColor(ContextCompat.getColorStateList(this.context, R.color.item_icon_tint_bottom))
                setupSelection(position == selectionPosition)
                setOnClickListener {
                    if (selectionPosition != position) {
                        onPermissionChanged(permission)
                        selectionPosition = position
                        notifyItemRangeChanged(0, itemCount)
                    }
                }
            }
            permissionTitle.setText(permission.translation)
            permissionDescription.text = context.getString(permission.description, AccountUtils.getCurrentDrive()?.name)

            val shapeAppearanceModel = ShapeAppearanceModel()
                .toBuilder()
                .setAllCornerSizes(RelativeCornerSize(0.5F))
                .build()
            mainIcon.shapeAppearanceModel = shapeAppearanceModel

            when (permission) {
                FolderPermission.ONLY_ME -> {
                    currentUser?.let { user -> mainIcon.loadAvatar(user) }
                    permissionDescription.visibility = GONE
                }
                FolderPermission.INHERIT -> {
                    if (sharedUsers.isNotEmpty()) {
                        sharedUsers.firstOrNull()?.let { firstUser -> mainIcon.loadAvatar(firstUser) }
                        secondIcon.apply {
                            sharedUsers.getOrNull(1)?.let { user ->
                                visibility = VISIBLE
                                loadAvatar(user)
                            }
                        }
                        thirdIcon.apply {
                            if (sharedUsers.size > 2) {
                                visibility = VISIBLE
                                remainingText.visibility = VISIBLE
                                remainingText.text = "+${sharedUsers.size - 2}"
                            }
                        }
                    }
                }
                else -> {
                    mainIcon.load(permission.icon)
                    mainIcon.shapeAppearanceModel = ShapeAppearanceModel()

                    when {
                        permission == ShareLink.ShareLinkPermission.PASSWORD -> {
                            val enabled = AccountUtils.getCurrentDrive()?.packFunctionality?.canSetSharelinkPassword == true
                            enableViewHolder(enabled)
                            if (!enabled) {
                                upgradeOffer.visibility = VISIBLE
                                upgradeOffer.setOnClickListener {
                                    onUpgradeOfferClicked?.invoke()
                                }
                            }
                        }
                        permission == Shareable.ShareablePermission.MANAGE && isExternalUser -> {
                            enableViewHolder(false)
                            userExternalWarning.visibility = VISIBLE
                        }
                        else -> enableViewHolder(true)
                    }
                }
            }
        }
    }

    private fun View.enableViewHolder(enabled: Boolean) {
        isEnabled = enabled
        disabled.visibility = if (enabled) GONE else VISIBLE
        upgradeOffer.visibility = GONE
        userExternalWarning.visibility = GONE
    }

    private fun MaterialCardView.setupSelection(enabled: Boolean) {
        if (showSelectionCheckIcon) isChecked = enabled
        strokeWidth = if (enabled) 2.toPx() else 0
        strokeColor = ContextCompat.getColor(context, R.color.primary)
        invalidate()
    }

    override fun getItemCount() = permissionList.size
}