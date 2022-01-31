/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.android.parcel.Parcelize

@Parcelize
open class DriveUser(
    @PrimaryKey override var id: Int = -1,
    @SerializedName("avatar_url")
    var avatarUrl: String? = "",
    @SerializedName("display_name")
    var displayName: String = "",
    var avatar: String = "",
    var email: String = "",
    override var permission: String = "",
    @SerializedName("invit_drive")
    var invitDrive: Boolean = false,
    @SerializedName("invit_drive_id")
    var invitDriveId: Int = -1,
    var role: String = "",
    var status: String = "",
    var type: String = "",
) : RealmObject(), Parcelable, Shareable {

    fun isExternalUser(): Boolean = type == Type.SHARED.value

    fun getUserAvatar(): String {
        return if (avatar.isNotBlank()) avatar else avatarUrl.toString()
    }

    enum class Type(val value: String) {
        MAIN("main"),
        SHARED("shared")
    }
}