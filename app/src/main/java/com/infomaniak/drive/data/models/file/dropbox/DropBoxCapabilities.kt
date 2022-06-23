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
package com.infomaniak.drive.data.models.file.dropbox

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize

@Parcelize
@RealmClass(embedded = true)
open class DropBoxCapabilities(
    @SerializedName("has_password")
    var hasPassword: Boolean = false,
    @SerializedName("has_notification")
    var hasNotification: Boolean = false,
    @SerializedName("has_validity")
    var hasValidity: Boolean = false,
    @SerializedName("has_size_limit")
    var hasSizeLimit: Boolean = false,
    var validity: DropBoxValidity? = null,
    var size: DropBoxSize? = null,
) : RealmObject(), Parcelable
