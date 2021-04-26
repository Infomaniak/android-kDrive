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
package com.infomaniak.drive.data.models.drive

import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class DrivePackFunctionality(
    @PrimaryKey var driveId: Int = -1,
    @SerializedName("can_set_sharelink_expiration")
    var canSetSharelinkExpiration: Boolean = false,
    @SerializedName("can_set_sharelink_password")
    var canSetSharelinkPassword: Boolean = false,
    @SerializedName("has_team_space")
    var hasTeamSpace: Boolean = false,
    @SerializedName("manage_right")
    var manageRight: Boolean = false,
    @SerializedName("number_of_versions")
    var numberOfVersions: Long = 0,
    @SerializedName("versions_kept_for_days")
    var versionsKeptForDays: Long = 0,
    var dropbox: Boolean = false,
    var versioning: Boolean = false
) : RealmObject()