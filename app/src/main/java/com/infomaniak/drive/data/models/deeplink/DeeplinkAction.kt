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

import android.net.Uri
import kotlinx.parcelize.Parcelize


@Parcelize
sealed interface DeeplinkAction : DeeplinkType {
    data class Collaborate(override val originalUri: Uri, val driveId: Int, val uuid: String) : DeeplinkAction {
        override val isHandled: Boolean
            get() = false
    }

    data class Drive(override val originalUri: Uri, val driveId: Int, val roleFolder: RoleFolder) : DeeplinkAction {
        override val isHandled: Boolean
            get() = roleFolder.isHandled
    }

    data class Office(override val originalUri: Uri, val driveId: Int, val fileId: Int) : DeeplinkAction

    companion object {
        @Throws(InvalidValue::class)
        fun from(originalUri: Uri, actionType: String, action: String): DeeplinkAction =
            ActionType.from(actionType).build(originalUri, action)
    }
}
