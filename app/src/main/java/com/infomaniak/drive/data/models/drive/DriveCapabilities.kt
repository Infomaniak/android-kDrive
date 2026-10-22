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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@RealmClass(embedded = true)
open class DriveCapabilities(
    @SerialName("use_versioning")
    @SerializedName("use_versioning")
    var useVersioning: Boolean = false,
    @SerialName("use_upload_compression")
    @SerializedName("use_upload_compression")
    var useUploadCompression: Boolean = false,
    @SerialName("use_team_space")
    @SerializedName("use_team_space")
    var useTeamSpace: Boolean = false,
    @SerialName("can_add_user")
    @SerializedName("can_add_user")
    var canAddUser: Boolean = false,
    @SerialName("can_see_stats")
    @SerializedName("can_see_stats")
    var canSeeStats: Boolean = false,
    @SerialName("can_upgrade_to_ksuite")
    @SerializedName("can_upgrade_to_ksuite")
    var canUpgradeToKsuite: Boolean = false,
    @SerialName("can_rewind")
    @SerializedName("can_rewind")
    var canRewind: Boolean = false
) : RealmObject()
