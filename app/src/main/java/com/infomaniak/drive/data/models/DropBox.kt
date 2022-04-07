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
import java.util.*

@Parcelize
@RealmClass(embedded = true)
data class DropBox(
    var id: Int = -1,
    val name: String = "",
    var capabilities: DropBoxCapabilities? = null,
    var url: String = "",
    var uuid: String = "",
    @SerializedName("created_at") val createdAt: Date? = null,
    @SerializedName("created_by") val createdBy: Int = -1,
    @SerializedName("last_uploaded_at") val lastUploadedAt: Long? = null,
    @SerializedName("nb_users") val collaborativeUsersCount: Int,
    @SerializedName("updated_at") val updatedAt: Date? = null,
) : RealmObject(), Parcelable {

    inline val hasNotification: Boolean get() = capabilities?.hasNotification == true //when someone upload a file
    inline val hasPassword: Boolean get() = capabilities?.hasPassword == true
    inline val limitFileSize: Long? get() = capabilities?.size?.limit
    inline val validUntil: Date? get() = capabilities?.validity?.date

    /**
     * Local
     */
    var newPassword = false
    var newPasswordValue: String? = null
    var newHasNotification = false
    var newLimitFileSize: Long? = null
    var withLimitFileSize = false
    var newValidUntil: Date? = null

    fun initLocalValue() {
        newPassword = hasPassword
        newHasNotification = hasNotification
        newLimitFileSize = limitFileSize
        withLimitFileSize = limitFileSize != null
        newValidUntil = validUntil
    }

    @Parcelize
    @RealmClass(embedded = true)
    open class DropBoxCapabilities(
        @SerializedName("has_password") val hasPassword: Boolean,
        @SerializedName("has_notification") val hasNotification: Boolean,
        @SerializedName("has_validity") val hasValidity: Boolean,
        @SerializedName("has_size_limit") val hasSizeLimit: Boolean,
        var validity: DropBoxValidity?,
        var size: DropBoxSize?,
    ) : RealmObject(), Parcelable

    @Parcelize
    @RealmClass(embedded = true)
    open class DropBoxValidity(
        var date: Date? = null,
        @SerializedName("has_expired") var hasExpired: Boolean? = null,
    ) : RealmObject(), Parcelable

    @Parcelize
    @RealmClass(embedded = true)
    open class DropBoxSize(
        var limit: Long? = null,
        var remaining: Int? = null,
    ) : RealmObject(), Parcelable
}