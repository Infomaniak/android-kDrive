/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.deeplink

import kotlinx.parcelize.Parcelize


@Parcelize
sealed interface DeeplinkAction : DeeplinkType {

    data class Collaborate(val driveId: String, val uuid: String) : DeeplinkAction

    data class Drive(val driveId: String, val roleFolder: RoleFolder) : DeeplinkAction

    data class Office(val driveId: String, val uuid: String) : DeeplinkAction

    companion object {
        @Throws(InvalidValue::class)
        fun from(actionType: String, action: String): DeeplinkAction =
            ActionType.from(actionType).build(action)
    }
}
