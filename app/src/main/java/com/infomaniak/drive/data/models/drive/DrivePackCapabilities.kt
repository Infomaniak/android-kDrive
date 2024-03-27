/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
open class DrivePackCapabilities(
    @SerializedName("use_vault")
    var useVault: Boolean = false,
    @SerializedName("use_manage_right")
    var useManageRight: Boolean = false,
    @SerializedName("can_set_trash_duration")
    var canSetTrashDuration: Boolean = false,
    @SerializedName("use_dropbox")
    var useDropbox: Boolean = false,
    @SerializedName("can_rewind")
    var canRewind: Boolean = false,
    @SerializedName("use_folder_custom_color")
    var useFolderCustomColor: Boolean = false,
    @SerializedName("can_access_dashboard")
    var canAccessDashboard: Boolean = false,
    @SerializedName("can_set_sharelink_password")
    var canSetSharelinkPassword: Boolean = false,
    @SerializedName("can_set_sharelink_expiration")
    var canSetSharelinkExpiration: Boolean = false,
    @SerializedName("can_set_sharelink_custom_url")
    var canSetSharelinkCustomUrl: Boolean = false
) : RealmObject()
