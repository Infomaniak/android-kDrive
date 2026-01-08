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
package com.infomaniak.drive.data.models

import android.os.Parcelable
import com.infomaniak.drive.R
import kotlinx.parcelize.Parcelize

interface Shareable : Parcelable {
    var id: Int
    var right: String

    fun getFilterValue(): String {
        return when (this) {
            is DriveUser -> displayName
            is Invitation -> email
            is Team -> name
            else -> ""
        }
    }

    fun getFilePermission(isExternal: Boolean = false): ShareablePermission = when (right) {
        ShareablePermission.READ.apiValue -> ShareablePermission.READ
        ShareablePermission.WRITE.apiValue -> if (isExternal) ShareablePermission.WRITE_EXTERNAL else ShareablePermission.WRITE
        ShareablePermission.MANAGE.apiValue -> ShareablePermission.MANAGE
        else -> ShareablePermission.READ
    }

    @Parcelize
    enum class ShareablePermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int?,
        val apiValue: String
    ) : Permission {

        READ(
            icon = R.drawable.ic_view,
            translation = R.string.userPermissionRead,
            description = R.string.userPermissionReadDescription,
            apiValue = "read"
        ),

        WRITE(
            icon = R.drawable.ic_edit,
            translation = R.string.userPermissionWrite,
            description = R.string.userPermissionWriteDescription,
            apiValue = "write"
        ),

        WRITE_EXTERNAL(
            icon = R.drawable.ic_edit,
            translation = R.string.userPermissionWrite,
            description = R.string.userPermissionWriteExternalDescription,
            apiValue = "write"
        ),

        MANAGE(
            icon = R.drawable.ic_crown,
            translation = R.string.userPermissionManage,
            description = R.string.userPermissionManageDescription,
            apiValue = "manage"
        ),

        DELETE(
            icon = R.drawable.ic_bin,
            translation = R.string.buttonDelete,
            description = R.string.userPermissionRemove,
            apiValue = "delete"
        ),

        REMOVE_DRIVE_ACCESS(
            icon = R.drawable.ic_bin,
            translation = R.string.buttonRemoveDriveAccess,
            description = null,
            apiValue = "remove_from_drive"
        )
    }
}
