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

import com.infomaniak.drive.data.models.deeplink.ExternalFileType.Companion.extractExternalFileType
import com.infomaniak.drive.data.models.deeplink.FileType.Companion.extractFileType


enum class FolderType(val type: String, vararg val propertiesPattern: String) {
    Collaboratives(type = "collaboratives"),
    Favorites(type = "favorites", PREVIEW),
    Files(type = "files", *FileType.FOLDER_PROPERTIES),
    MyShares(type = "my-shares", PREVIEW),
    SharedWithMe(type = "shared-with-me", *ExternalFileType.SHARED_WITH_ME_FOLDER_PROPERTIES),
    SharedLinks(type = "shared-links", PREVIEW),
    Recents(type = "recents", PREVIEW),
    Trash(type = "trash", FOLDER_ID);

    fun build(folderProperties: String): RoleFolder = folderProperties.optionalFind(*propertiesPattern).run {
        when (this@FolderType) {
            Collaboratives -> RoleFolder.Collaboratives
            Favorites -> RoleFolder.Favorites(fileId = parseOptionalId(GROUP_FILE_ID))
            Files -> RoleFolder.Files(fileType = extractFileType())
            MyShares -> RoleFolder.MyShares(fileId = parseOptionalId(GROUP_FILE_ID))
            SharedWithMe -> RoleFolder.SharedWithMe(fileType = extractExternalFileType())
            SharedLinks -> RoleFolder.SharedLinks(fileId = parseOptionalId(GROUP_FILE_ID))
            Recents -> RoleFolder.Recents(fileId = parseOptionalId(GROUP_FILE_ID))
            Trash -> RoleFolder.Trash(folderId = parseOptionalId(GROUP_FOLDER_ID))
        }
    }

    companion object {
        @Throws(InvalidFormatting::class)
        fun from(value: String): FolderType = entries.find { it.type == value } ?: throw InvalidFormatting()

        fun String.optionalFind(vararg propertiesPattern: String): MatchResult? {
            return propertiesPattern.firstNotNullOfOrNull { Regex(it).find(this) }
        }
    }
}
