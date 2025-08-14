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
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass(embedded = true)
open class DrivePack(
    var id: Int = 0,
    var name: String = "",
    @SerializedName("capabilities")
    private var _capabilities: DrivePackCapabilities? = DrivePackCapabilities()
) : RealmObject() {

    val capabilities: DrivePackCapabilities
        get() = _capabilities ?: DrivePackCapabilities()

    val type: DrivePackType? get() = enumValueOfOrNull<DrivePackType>(name.uppercase())

    enum class DrivePackType {
        FREE, // Old offer pack, now replaced by [KSuite.PersoFree]
        SOLO, // Old offer pack, now replaced by [KSuite.PersoPlus]
        TEAM, // Old offer pack, will hopefully be replaced someday
        PRO, // Old offer pack, will hopefully be replaced someday
        MY_KSUITE, // [KSuite.PersoFree]
        MY_KSUITE_PLUS, // [KSuite.PersoPlus]
        KSUITE_ESSENTIAL, // [KSuite.ProFree]
        KSUITE_STANDARD, // [KSuite.ProStandard]
        KSUITE_PRO, // [KSuite.ProBusiness]
        KSUITE_ENTREPRISE, // [KSuite.ProEnterprise]
    }
}
