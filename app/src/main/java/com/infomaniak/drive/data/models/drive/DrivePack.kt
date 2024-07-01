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
open class DrivePack(
    var id: Int = 0,
    var name: String = "",
    @SerializedName("capabilities")
    private var _capabilities: DrivePackCapabilities? = DrivePackCapabilities()
) : RealmObject() {

    val capabilities: DrivePackCapabilities
        get() = _capabilities ?: DrivePackCapabilities()

    val type: DrivePackType? get() = drivePackTypeOf(id)

    private fun drivePackTypeOf(id: Int) = when (id) {
        DrivePackType.SOLO.id -> DrivePackType.SOLO
        DrivePackType.TEAM.id -> DrivePackType.TEAM
        DrivePackType.PRO.id -> DrivePackType.PRO
        DrivePackType.FREE.id -> DrivePackType.FREE
        DrivePackType.KSUITE_STANDARD.id -> DrivePackType.KSUITE_STANDARD
        DrivePackType.KSUITE_PRO.id -> DrivePackType.KSUITE_PRO
        DrivePackType.KSUITE_ENTREPRISE.id -> DrivePackType.KSUITE_ENTREPRISE
        else -> null
    }

    enum class DrivePackType(val id: Int) {
        SOLO(1),
        TEAM(2),
        PRO(3),
        FREE(6),
        KSUITE_STANDARD(8),
        KSUITE_PRO(11),
        KSUITE_ENTREPRISE(14),
    }
}
