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
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class DropBox(
    val id: Int,
    val url: String,
    val uuid: String,
    val alias: String,
    val user: DriveUser,
    val password: Boolean,
    @SerializedName("created_by") val createdBy: Int,
    @SerializedName("created_at") val createdAt: Date,
    @SerializedName("updated_at") val updatedAt: Date?,
    @SerializedName("valid_until") val validUntil: Date?,
    @SerializedName("limit_remaining") val limitRemaining: Long?,
    @SerializedName("limit_file_size") val limitFileSize: Long?,
    @SerializedName("last_uploaded_at") val lastUploadedAt: Long?,
    @SerializedName("email_when_finished") val emailWhenFinished: Boolean,
    @SerializedName("collaborative_users_count") val collaborativeUsersCount: Int
) : Parcelable {
    /**
     * Local
     */
    var newPassword = false
    var newPasswordValue: String? = null
    var newEmailWhenFinished = false
    var newLimitFileSize: Long? = null
    var withLimitFileSize = false
    var newValidUntil: Date? = null

    fun initLocalValue() {
        newPassword = password
        newEmailWhenFinished = emailWhenFinished
        newLimitFileSize = limitFileSize
        withLimitFileSize = limitFileSize != null
        newValidUntil = validUntil
    }
}