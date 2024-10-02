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

@Parcelize
@RealmClass(embedded = true)
open class Rights(
    @SerializedName("can_become_dropbox")
    var canBecomeDropbox: Boolean = false,
    @SerializedName("can_become_sharelink")
    var canBecomeShareLink: Boolean = false,
    @SerializedName("can_create_directory")
    var canCreateDirectory: Boolean = false,
    @SerializedName("can_create_file")
    var canCreateFile: Boolean = false,
    @SerializedName("can_delete")
    var canDelete: Boolean = false,
    @SerializedName("can_leave")
    var canLeave: Boolean = false,
    @SerializedName("can_move")
    var canMove: Boolean = false,
    @SerializedName("can_move_into")
    var canMoveInto: Boolean = false,
    @SerializedName("can_read")
    var canRead: Boolean = false,
    @SerializedName("can_rename")
    var canRename: Boolean = false,
    @SerializedName("can_share")
    var canShare: Boolean = false,
    @SerializedName("can_show")
    var canShow: Boolean = false,
    @SerializedName("can_upload")
    var canUpload: Boolean = false,
    @SerializedName("can_use_favorite")
    var canUseFavorite: Boolean = false,
    @SerializedName("can_use_team")
    var canUseTeam: Boolean = false,
    @SerializedName("can_write")
    var canWrite: Boolean = false,
    @SerializedName("colorable")
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
