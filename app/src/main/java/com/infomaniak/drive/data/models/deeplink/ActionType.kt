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

internal enum class ActionType(val type: String, val actionPattern: String) {
    Collaborate(type = "collaborate", actionPattern = "$DRIVE_ID/$UUID"),
    Drive(type = "drive", actionPattern = "$DRIVE_ID/$ROLE_FOLDER/$FOLDER_ALL_PROPERTIES"),
    Office(type = "office", actionPattern = "$DRIVE_ID/$FILE_ID");

    fun build(originalUri: Uri, action: String): DeeplinkAction = action.find(actionPattern).run {
        when (this@ActionType) {
            Collaborate -> DeeplinkAction.Collaborate(
                originalUri = originalUri,
                driveId = parseId(1),
                uuid = groupValues[2],
            )
            Drive -> DeeplinkAction.Drive(
                originalUri = originalUri,
                driveId = parseId(1),
                roleFolder = RoleFolder.from(folderType = groupValues[2], folderProperties = groupValues[3])
            )
            Office -> DeeplinkAction.Office(
                originalUri = originalUri,
                driveId = parseId(1),
                fileId = parseId(2),
            )
        }
    }

    companion object {
        fun from(value: String): ActionType = entries.find { it.type == value } ?: throw InvalidValue()
        fun String.find(actionPattern: String): MatchResult =
            Regex(actionPattern).find(this) ?: throw InvalidValue()
    }
}
