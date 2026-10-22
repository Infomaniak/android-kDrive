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
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@RealmClass(embedded = true)
open class Rights(
    @SerialName("can_become_dropbox")
    @SerializedName("can_become_dropbox")
    var canBecomeDropbox: Boolean = false,
    @SerialName("can_become_sharelink")
    @SerializedName("can_become_sharelink")
    var canBecomeShareLink: Boolean = false,
    @SerialName("can_create_directory")
    @SerializedName("can_create_directory")
    var canCreateDirectory: Boolean = false,
    @SerialName("can_create_file")
    @SerializedName("can_create_file")
    var canCreateFile: Boolean = false,
    @SerialName("can_delete")
    @SerializedName("can_delete")
    var canDelete: Boolean = false,
    @SerialName("can_leave")
    @SerializedName("can_leave")
    var canLeave: Boolean = false,
    @SerialName("can_move")
    @SerializedName("can_move")
    var canMove: Boolean = false,
    @SerialName("can_move_into")
    @SerializedName("can_move_into")
    var canMoveInto: Boolean = false,
    @SerialName("can_read")
    @SerializedName("can_read")
    var canRead: Boolean = false,
    @SerialName("can_rename")
    @SerializedName("can_rename")
    var canRename: Boolean = false,
    @SerialName("can_share")
    @SerializedName("can_share")
    var canShare: Boolean = false,
    @SerialName("can_show")
    @SerializedName("can_show")
    var canShow: Boolean = false,
    @SerialName("can_upload")
    @SerializedName("can_upload")
    var canUpload: Boolean = false,
    @SerialName("can_use_favorite")
    @SerializedName("can_use_favorite")
    var canUseFavorite: Boolean = false,
    @SerialName("can_use_team")
    @SerializedName("can_use_team")
    var canUseTeam: Boolean = false,
    @SerialName("can_write")
    @SerializedName("can_write")
    var canWrite: Boolean = false,
    var colorable: Boolean = false,
    var right: String = "",
) : RealmObject(), Parcelable {

    enum class Right(val value: String) {
        ACCESS_READ("read"),
        ACCESS_WRITE("write"),
        ACCESS_MANAGE("manage"),
        ACCESS_NONE("none"),
        ACCESS_LIMITED("limited"),
        ACCESS_INHERITED("inherited"),
        ACCESS_UNDEFINED("undefined"),
        ACCESS_PRIVATE("private"),
    }
}
