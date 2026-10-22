/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class UserFileAccess(
    override var id: Int = -1,
    var name: String = "",
    override var right: String = "",
    var color: Int? = null,
    var status: UserFileAccessStatus = UserFileAccessStatus.ACTIVE,
    var email: String = "",
    var user: DriveUser? = null,
    var role: DriveUser.Role = DriveUser.Role.NONE,
) : Shareable {

    override val isExternalUser
        get() = role == DriveUser.Role.EXTERNAL

    @Serializable
    enum class UserFileAccessStatus {
        /** User has access to the Drive */
        @SerialName("active")
        @SerializedName("active")
        ACTIVE,

        /** User has been removed but his data remain in the drive */
        @SerialName("deleted_kept")
        @SerializedName("deleted_kept")
        DELETED_KEPT,

        /** User has been removed */
        @SerialName("deleted_removed")
        @SerializedName("deleted_removed")
        DELETED_REMOVED,

        /** User has been removed and his data has been transferred to another user */
        @SerialName("deleted_transferred")
        @SerializedName("deleted_transferred")
        DELETED_TRANSFERRED,

        /** User has been locked, user can no longer access to the drive */
        @SerialName("locked")
        @SerializedName("locked")
        LOCKED,

        /** User has not accepted the invitation request */
        @SerialName("pending")
        @SerializedName("pending")
        PENDING
    }
}
