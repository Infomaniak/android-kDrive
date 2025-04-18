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
package com.infomaniak.drive.data.models.drive

import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date

open class Drive(

    @PrimaryKey var objectId: String = "",

    /**
     * User data
     */
    @SerializedName("account_admin")
    var accountAdmin: Boolean = false,
    @SerializedName("rights")
    private var _rights: DriveRights? = DriveRights(),
    var name: String = "",
    @SerializedName("preferences")
    private var _preferences: DrivePreferences? = DrivePreferences(),
    @SerializedName("role")
    private var _role: String = "",
    @SerializedName("capabilities")
    private var _capabilities: DriveCapabilities? = DriveCapabilities(),
    @SerializedName("quota")
    private var _quotas: DriveQuotas? = DriveQuotas(),
    var sharedWithMe: Boolean = false,
    var userId: Int = 0,
    @SerializedName("categories_permissions")
    private var _categoryRights: CategoryRights? = CategoryRights(),

    /**
     * Drive data
     */
    @SerializedName("account_id")
    var accountId: Int = -1,
    @SerializedName("account")
    private var _driveAccount: DriveAccount? = null,
    @SerializedName("created_at")
    var createdAt: Long = 0,
    @SerializedName("updated_at")
    var updatedAt: Long = 0,
    @SerializedName("used_size")
    var usedSize: Long = 0,
    var id: Int = -1,
    @SerializedName("in_maintenance")
    var maintenance: Boolean = false,
    @SerializedName("maintenance_reason")
    var maintenanceReason: String = "",
    var pack: DrivePack? = DrivePack(),
    var size: Long = 0,
    var version: String = "",
    @SerializedName("users")
    private var _users: DriveUsersCategories? = DriveUsersCategories(),
    @SerializedName("teams")
    private var _teams: DriveTeamsCategories? = DriveTeamsCategories(),
    var categories: RealmList<Category> = RealmList(),
) : RealmObject() {

    val driveAccount: DriveAccount
        get() = _driveAccount ?: DriveAccount()

    val preferences: DrivePreferences
        get() = _preferences ?: DrivePreferences()

    val capabilities: DriveCapabilities
        get() = _capabilities ?: DriveCapabilities()

    val categoryRights: CategoryRights
        get() = _categoryRights ?: CategoryRights()

    val users: DriveUsersCategories
        get() = _users ?: DriveUsersCategories()

    val teams: DriveTeamsCategories
        get() = _teams ?: DriveTeamsCategories()

    val rights: DriveRights
        get() = _rights ?: DriveRights()

    val quotas: DriveQuotas
        get() = _quotas ?: DriveQuotas()

    val role: DriveUser.Role?
        get() = enumValueOfOrNull<DriveUser.Role>(_role)

    // Old offer pack, now replaced by My kSuite
    inline val isFreePack get() = pack?.type == DrivePack.DrivePackType.FREE
    // Old offer pack, now replaced by My kSuite Plus
    inline val isSoloPack get() = pack?.type == DrivePack.DrivePackType.SOLO
    inline val isMyKSuitePack get() = pack?.type == DrivePack.DrivePackType.MY_KSUITE
    inline val isMyKSuitePlusPack get() = pack?.type == DrivePack.DrivePackType.MY_KSUITE_PLUS
    inline val isFreeTier get() = isFreePack || isMyKSuitePack
    inline val isSingleUserDrive get() = isFreeTier || isMyKSuitePlusPack || isSoloPack

    inline val isTechnicalMaintenance get() = maintenanceReason == MaintenanceReason.TECHNICAL.value

    inline val canCreateDropbox get() = pack?.capabilities?.useDropbox == true && (!isFreeTier || quotas.canCreateDropbox)
    inline val canCreateShareLink get() = !isFreeTier || quotas.canCreateShareLink

    inline val isDriveFull get() = usedSize >= size

    fun isUserAdmin(): Boolean = role == DriveUser.Role.ADMIN

    fun isDriveUser(): Boolean = role != DriveUser.Role.NONE && role != DriveUser.Role.EXTERNAL

    fun getUpdatedAt(): Date = Date(updatedAt * 1000)

    override fun equals(other: Any?): Boolean {
        return if (other is Drive) {
            objectId == other.objectId
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = objectId.hashCode()

    enum class MaintenanceReason(val value: String) {
        NOT_RENEW("not_renew"),
        DEMO_END("demo_end"),
        INVOICE_OVERDUE("invoice_overdue"),
        TECHNICAL("technical"),
    }
}
