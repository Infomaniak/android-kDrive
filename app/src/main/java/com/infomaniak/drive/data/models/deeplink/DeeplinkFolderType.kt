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

import com.infomaniak.drive.data.models.deeplink.DeeplinkExternalFilePath.Companion.extractExternalFileType
import com.infomaniak.drive.data.models.deeplink.DeeplinkFilePath.Companion.extractFileType


enum class DeeplinkFolderType(val type: String, vararg val propertiesPattern: String) {
    Collaboratives(type = "collaboratives"),
    Favorites(type = "favorites", PREVIEW_PATTERN),
    Files(type = "files", *DeeplinkFilePath.PATH_IDS_PATTERN),
    MyShares(type = "my-shares", PREVIEW_PATTERN),
    Recents(type = "recents", PREVIEW_PATTERN),
    Redirect(type = "redirect", FILE_ID),
    SharedWithMe(type = "shared-with-me", *DeeplinkExternalFilePath.PATH_IDS_PATTERN),
    SharedLinks(type = "shared-links", PREVIEW_PATTERN),
    Trash(type = "trash", FOLDER_ID);

    fun build(folderProperties: String): DeeplinkFolderRole = folderProperties.optionalFind(*propertiesPattern).run {
        when (this@DeeplinkFolderType) {
            Collaboratives -> DeeplinkFolderRole.Collaboratives
            Favorites -> DeeplinkFolderRole.Favorites(fileId = parseOptionalId(GROUP_FILE_ID))
            Files -> DeeplinkFolderRole.Files(filePath = extractFileType())
            MyShares -> DeeplinkFolderRole.MyShares(fileId = parseOptionalId(GROUP_FILE_ID))
            Recents -> DeeplinkFolderRole.Recents(fileId = parseOptionalId(GROUP_FILE_ID))
            Redirect -> DeeplinkFolderRole.Redirect(fileId = parseId(GROUP_FILE_ID))
            SharedWithMe -> DeeplinkFolderRole.SharedWithMe(externalFilePath = extractExternalFileType())
            SharedLinks -> DeeplinkFolderRole.SharedLinks(fileId = parseOptionalId(GROUP_FILE_ID))
            Trash -> DeeplinkFolderRole.Trash(folderId = parseOptionalId(GROUP_FOLDER_ID))
        }
    }

    companion object {
        @Throws(InvalidFormatting::class)
        fun from(value: String): DeeplinkFolderType = entries.find { it.type == value } ?: throw InvalidFormatting()

        fun String.optionalFind(vararg propertiesPattern: String): MatchResult? {
            return propertiesPattern.firstNotNullOfOrNull { Regex(it).find(this) }
        }
    }
}
