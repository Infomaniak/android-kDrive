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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.drive.data.models.File.FolderPermission
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.Share.UserFileAccess
import com.infomaniak.drive.data.models.ShareLink.ShareLinkDocumentPermission
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFilePermission
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFolderPermission
import com.infomaniak.drive.data.models.Shareable.ShareablePermission
import com.infomaniak.drive.databinding.CardviewPermissionBinding
import com.infomaniak.drive.ui.fileList.fileShare.PermissionsAdapter.PermissionsViewHolder
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.toPx

class PermissionsAdapter(
    var selectionPosition: Int? = null,
    private var currentUser: User? = null,
    private var isExternalUser: Boolean = false,
    private var sharedUsers: ArrayList<UserFileAccess> = ArrayList(),
    private val onPermissionChanged: (newPermission: Permission) -> Unit,
) : Adapter<PermissionsViewHolder>() {

    var permissionList: ArrayList<Permission> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionsViewHolder {
        return PermissionsViewHolder(CardviewPermissionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    fun setAll(newPermissions: ArrayList<Permission>) {
        permissionList = newPermissions
        notifyItemRangeInserted(0, newPermissions.size)
    }

    fun setUsers(users: ArrayList<UserFileAccess>) {
        sharedUsers = users
    }

    fun addItem(permission: Permission) {
        permissionList.add(permission)
        notifyItemInserted(permissionList.size)
    }

    override fun onBindViewHolder(holder: PermissionsViewHolder, position: Int) = with(holder.binding) {
        val permission = permissionList[position]

        permissionCard.apply {
            setupSelection(position == selectionPosition)
            setOnClickListener {
                if (selectionPosition != position) {
                    onPermissionChanged(permission)
                    selectionPosition = position
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }

        setupTexts(permission)
        setupMainIcon()

        when (permission) {
            FolderPermission.ONLY_ME -> setupOnlyMePermissionUi()
            FolderPermission.INHERIT -> setupInheritPermissionUi()
            else -> setupOthersPermissionUi(permission)
        }
    }

    private fun CardviewPermissionBinding.setupTexts(permission: Permission) {
        permissionTitle.setText(permission.translation)
        permissionDescription.text = context.getString(permission.description, AccountUtils.getCurrentDrive()?.name)
    }

    private fun CardviewPermissionBinding.setupMainIcon() {
        mainIcon.shapeAppearanceModel = ShapeAppearanceModel()
            .toBuilder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()
    }

    private fun CardviewPermissionBinding.setupOnlyMePermissionUi() {
        currentUser?.let { user -> mainIcon.loadAvatar(user) }
        permissionDescription.isGone = true
    }

    private fun CardviewPermissionBinding.setupInheritPermissionUi() {
        if (sharedUsers.isNotEmpty()) {

            sharedUsers.firstOrNull()?.let { firstUser -> firstUser.user?.let { mainIcon.loadAvatar(it) } }

            secondIcon.apply {
                sharedUsers.getOrNull(1)?.user?.let { user ->
                    isVisible = true
                    loadAvatar(user)
                }
            }

            thirdIcon.apply {
                if (sharedUsers.size > 2) {
                    root.isVisible = true
                    remainingText.apply {
                        isVisible = true
                        text = "+${sharedUsers.size - 2}"
                    }
                }
            }
        }
    }

    private fun CardviewPermissionBinding.setupOthersPermissionUi(permission: Permission) {
        mainIcon.load(permission.icon)
        mainIcon.shapeAppearanceModel = ShapeAppearanceModel()

        when {
            permission == ShareablePermission.MANAGE && isExternalUser -> {
                enableViewHolder(false)
                userExternalWarning.isVisible = true
            }
            permission == ShareLinkFilePermission.PUBLIC ||
                    permission == ShareLinkFolderPermission.PUBLIC ||
                    permission == ShareLinkDocumentPermission.PUBLIC -> {
                enableViewHolder(true)
                AccountUtils.getCurrentDrive()?.let { drive ->
                    if (drive.kSuite == KSuite.Pro.Free) {
                        upgradeOffer.isVisible = true
                        kSuiteProChip.isVisible = true
                    }
                }
            }
            else -> enableViewHolder(true)
        }
    }

    private fun CardviewPermissionBinding.enableViewHolder(enabled: Boolean) {
        root.isEnabled = enabled
        disabled.isGone = enabled
        upgradeOffer.isGone = true
        userExternalWarning.isGone = true
    }

    private fun MaterialCardView.setupSelection(enabled: Boolean) {
        strokeWidth = if (enabled) 2.toPx() else 0
        invalidate()
    }

    override fun getItemCount() = permissionList.size

    class PermissionsViewHolder(val binding: CardviewPermissionBinding) : ViewHolder(binding.root)
}
