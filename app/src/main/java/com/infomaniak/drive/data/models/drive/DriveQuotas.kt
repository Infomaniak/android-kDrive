/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.drive

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass(embedded = true)
open class DriveQuotas(
    var dropbox: DriveQuota? = null,
    @SerializedName("shared_link")
    var sharedLink: DriveQuota? = null,
) : RealmObject() {

    inline val canCreateDropbox get() = dropbox?.let { it.current < it.max } ?: true
    inline val canCreateShareLink get() = sharedLink?.let { it.current < it.max } ?: true
}
