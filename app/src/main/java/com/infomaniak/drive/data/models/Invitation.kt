/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Invitation(
    override val id: Int = -1,
    val role: String = "",
    val type: String = "",
    val email: String = "",
    val status: String = "",
    val avatar: String = "",
    override var permission: String = "",
    @SerializedName("user_id") val userId: Int = -1,
    @SerializedName("invit_drive") val invitDrive: Boolean = false,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("invit_drive_id") val invitDriveId: Int = -1,
) : Parcelable, Shareable