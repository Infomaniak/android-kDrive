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

internal enum class ActionType(val type: String, val pattern: String) {
    Collaborate(type = "collaborate", pattern = "$DRIVE_ID/$UUID"),
    Drive(type = "drive", pattern = "$DRIVE_ID/$ROLE_FOLDER/$FOLDER_ALL_PROPERTIES"),
    Office(type = "office", pattern = "$DRIVE_ID/$UUID");

    fun build(action: String): DeeplinkAction = action.toItems(pattern).let { items ->
        when (this) {
            Collaborate -> DeeplinkAction.Collaborate(driveId = items[1], uuid = items[2])
            Drive -> DeeplinkAction.Drive(
                driveId = items[1],
                roleFolder = RoleFolder.from(folderType = items[2], folderProperties = items[3])
            )
            Office -> DeeplinkAction.Office(driveId = items[1], uuid = items[2])
        }
    }

    companion object {
        fun from(value: String): ActionType = entries.find { it.type == value } ?: throw InvalidValue()
        fun String.toItems(actionPattern: String): List<String> =
            Regex(actionPattern).find(this)?.groupValues ?: throw InvalidValue()
    }
}
