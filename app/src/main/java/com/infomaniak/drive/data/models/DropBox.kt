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
import com.infomaniak.drive.data.models.file.dropbox.DropBoxCapabilities
import com.infomaniak.drive.utils.serialization.ApiTimestampDateSerializer
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.RealmClass
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Date

@Serializable
@Parcelize
@RealmClass(embedded = true)
open class DropBox(
    var id: Int = -1,
    var name: String = "",
    var capabilities: DropBoxCapabilities? = null,
    var url: String = "",
    var uuid: String = "",
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerialName("created_at")
    var createdAt: Date? = null,
    @SerialName("created_by")
    var createdBy: Int = -1,
    @SerialName("last_uploaded_at")
    var lastUploadedAt: Long? = null,
    @SerialName("nb_users")
    var collaborativeUsersCount: Int = 0,
    @Serializable(with = ApiTimestampDateSerializer::class)
    @SerialName("updated_at")
    var updatedAt: Date? = null,
) : RealmObject(), Parcelable {

    inline val hasNotification: Boolean get() = capabilities?.hasNotification == true //when someone upload a file
    inline val hasPassword: Boolean get() = capabilities?.hasPassword == true
    inline val limitFileSize: Long? get() = capabilities?.size?.limit
    inline val validUntil: Date? get() = capabilities?.validity?.date

    /**
     * Local
     */
    @Ignore
    @Transient
    var newPassword = false

    @Ignore
    @Transient
    var newPasswordValue: String? = null

    @Ignore
    @Transient
    var newHasNotification = false

    @Ignore
    @Transient
    var newLimitFileSize: Long? = null

    @Ignore
    @Transient
    var newValidUntil: Date? = null

    fun initLocalValue() {
        newPassword = hasPassword
        newHasNotification = hasNotification
        newLimitFileSize = limitFileSize
        newValidUntil = validUntil
    }

}
