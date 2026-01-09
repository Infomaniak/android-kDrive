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
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class Share(
    val teams: ArrayList<Team> = arrayListOf(),
    val users: ArrayList<UserFileAccess> = arrayListOf(),
    val invitations: ArrayList<Invitation> = arrayListOf(),
    /**
     * Local
     */
    var driveUsers: ArrayList<DriveUser> = arrayListOf()
) {

    inline val members get() = invitations + teams + users

    @Parcelize
    data class UserFileAccess(
        override var id: Int = -1,
        var name: String = "",
        override var right: String = "",
        var color: Int? = null,
        var status: UserFileAccessStatus,
        var email: String = "",
        var user: DriveUser? = null,
        var role: DriveUser.Role,
    ) : Parcelable, Shareable {

        override val isExternalUser
            get() = role == DriveUser.Role.EXTERNAL

        enum class UserFileAccessStatus {
            /** User has access to the Drive */
            @SerializedName("active")
            ACTIVE,

            /** User has been removed but his data remain in the drive */
            @SerializedName("deleted_kept")
            DELETED_KEPT,

            /** User has been removed */
            @SerializedName("deleted_removed")
            DELETED_REMOVED,

            /** User has been removed and his data has been transferred to another user */
            @SerializedName("deleted_transferred")
            DELETED_TRANSFERRED,

            /** User has been locked, user can no longer access to the drive */
            @SerializedName("locked")
            LOCKED,

            /** User has not accepted the invitation request */
            @SerializedName("pending")
            PENDING
        }
    }
}
