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
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.firstOrEmpty
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
open class DriveUser(
    @PrimaryKey final override var id: Int = -1,
    @SerializedName("avatar_url")
    var avatarUrl: String? = "",
    @SerializedName("display_name")
    var displayName: String = "",
    private var avatar: String? = "",
    var email: String = "",
    @SerializedName("role")
    private var _role: String = "",
    /** Never used */
    override var right: String = "",
) : RealmObject(), Parcelable, Shareable {

    val role get() = enumValueOfOrNull<Role>(_role)

    inline val isExternalUser get() = role == Role.EXTERNAL

    constructor(user: User) : this() {
        id = user.id
        displayName = user.displayName ?: ""
    }

    fun getUserAvatar() = avatar?.ifBlank { avatarUrl.toString() } ?: avatarUrl.toString()

    fun getInitials(): String {
        displayName.split(" ").let { initials ->
            val initialFirst = initials.firstOrNull()?.firstOrEmpty()?.uppercase() ?: ""
            val initialSecond = initials.getOrNull(1)?.firstOrEmpty()?.uppercase() ?: ""
            return@getInitials "$initialFirst$initialSecond"
        }
    }

    enum class Role {
        @SerializedName("admin")
        ADMIN,

        @SerializedName("user")
        USER,

        @SerializedName("external")
        EXTERNAL
    }
}
