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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.utils.Utils
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Drive(

    @PrimaryKey var objectId: String = "",

    /**
     * User data
     */
    @SerializedName("can_add_user")
    var canAddUser: Boolean = false,
    @SerializedName("can_create_team_folder")
    var canCreateTeamFolder: Boolean = false,
    @SerializedName("has_technical_right")
    var hasTechnicalRight: Boolean = false,
    var name: String = "",
    @SerializedName("preferences")
    private var _preferences: DrivePreferences? = DrivePreferences(),
    private var role: String = "",
    var sharedWithMe: Boolean = false,
    var userId: Int = 0,
    @SerializedName("category_rights")
    var categoryRights: CategoryRights = CategoryRights(),

    /**
     * Drive data
     */
    @SerializedName("account_id")
    var accountId: Int = -1,
    @SerializedName("created_at")
    var createdAt: Long = 0,
    @SerializedName("updated_at")
    var updatedAt: Long = 0,
    @SerializedName("used_size")
    var usedSize: Long = 0,
    var id: Int = -1,
    var maintenance: Boolean = false,
    var pack: String = "",
    var size: Long = 0,
    var version: String = "",
    @SerializedName("pack_functionality")
    private var _packFunctionality: DrivePackFunctionality? = DrivePackFunctionality(),
    @SerializedName("users")
    private var _users: DriveUsersCategories? = DriveUsersCategories(),
    @SerializedName("teams")
    private var _teams: DriveTeamsCategories? = DriveTeamsCategories(),
    var categories: RealmList<Category> = RealmList(),
) : RealmObject() {

    val packFunctionality: DrivePackFunctionality
        get() = _packFunctionality ?: DrivePackFunctionality()

    val preferences: DrivePreferences
        get() = _preferences ?: DrivePreferences()

    val users: DriveUsersCategories
        get() = _users ?: DriveUsersCategories()

    val teams: DriveTeamsCategories
        get() = _teams ?: DriveTeamsCategories()

    fun convertToFile(rootName: String? = null): File {
        return File(
            id = if (rootName == null) id else Utils.ROOT_ID,
            driveId = id,
            lastModifiedAt = createdAt,
            name = rootName ?: name,
            rights = Rights(newFile = true),
            type = File.Type.DRIVE.value
        ).apply { driveColor = preferences.color }
    }

    fun isUserAdmin(): Boolean = role == "admin"

    override fun equals(other: Any?): Boolean {
        return if (other is Drive) {
            objectId == other.objectId
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return objectId.hashCode()
    }

    enum class DrivePack(val value: String) {
        FREE("free"),
        SOLO("solo"),
        TEAM("team"),
        PRO("pro")
    }

}