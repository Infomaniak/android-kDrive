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

import com.infomaniak.core.utils.ApiEnum
import com.infomaniak.core.utils.apiEnum
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.RealmClass
import kotlinx.serialization.SerialName

@RealmClass(embedded = true)
open class MaintenanceType : RealmObject() {
    @SerialName("code")
    private var _code: String? = null

    @delegate:Ignore
    val type: MaintenanceTypeValue? by apiEnum(::_code)

    enum class MaintenanceTypeValue(override val apiValue: String) : ApiEnum {
        ManagerInMaintenance("manager_in_maintenance"),
        ManagerIsBlocked("managerIsBlocked"),
        MoveNs("move_ns"),
        MoveSqlMaster("move_sql_cluster"),
        MoveSqlCluster("managerIsBlocked"),
        Rewind("rewind"),
        UpgradeSchema("upgrade_schema"),
        HardDelete("hard_delete"),
        Asleep("asleep"),
        WakingUp("waking_up"),
        Uninitializing("uninitializing"),
        Unknown("unknown")
    }
}
