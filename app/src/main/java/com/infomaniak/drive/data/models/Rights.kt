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
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize

@Parcelize
@RealmClass(embedded = true)
open class Rights(
    @SerializedName("can_become_collab") var canBecomeCollab: Boolean = false,
    @SerializedName("can_become_link") var canBecomeLink: Boolean = false,
    @SerializedName("can_favorite") var canFavorite: Boolean = false,
    var delete: Boolean = false,
    var leave: Boolean = false,
    var move: Boolean = false,
    @SerializedName("move_into") var moveInto: Boolean = false,
    @SerializedName("new_file") var newFile: Boolean = false,
    @SerializedName("new_folder") var newFolder: Boolean = false,
    var read: Boolean = false,
    var rename: Boolean = false,
    var right: String = "",
    var share: Boolean = false,
    var show: Boolean = false,
    @SerializedName("upload_new_file") var uploadNewFile: Boolean = false,
    var write: Boolean = false,
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