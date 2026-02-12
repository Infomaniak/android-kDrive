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


enum class FolderType(val type: String, val propertiesPattern: String = "") {
    Collaboratives(type = "collaboratives"),
    Favorites(type = "favorites", propertiesPattern = PREVIEW),
    Files(type = "files", propertiesPattern = FileType.FOLDER_PROPERTIES),
    MyShares(type = "my-shares", propertiesPattern = PREVIEW),
    SharedWithMe(type = "shared-with-me", propertiesPattern = ExternalFileType.SHARED_WITH_ME_FOLDER_PROPERTIES),
    SharedLinks(type = "shared-links", propertiesPattern = PREVIEW),
    Recents(type = "recents", propertiesPattern = PREVIEW),
    Trash(type = "trash", propertiesPattern = FOLDER_ID);

    fun build(folderProperties: String): RoleFolder = folderProperties.optionalFind(propertiesPattern).run {
        when (this@FolderType) {
            Collaboratives -> RoleFolder.Collaboratives
            Favorites -> RoleFolder.Favorites(fileId = parseOptionalId(1))
            Files -> RoleFolder.Files(fileType = extractFileType())
            MyShares -> RoleFolder.MyShares(fileId = parseOptionalId(1))
            SharedWithMe -> RoleFolder.SharedWithMe(fileType = extractExternalFileType())
            SharedLinks -> RoleFolder.SharedLinks(fileId = parseOptionalId(1))
            Recents -> RoleFolder.Recents(fileId = parseOptionalId(1))
            Trash -> RoleFolder.Trash(folderId = parseOptionalId(1))
        }
    }

    companion object {
        @Throws(InvalidValue::class)
        fun from(value: String): FolderType = entries.find { it.type == value } ?: throw InvalidValue()

        fun String.optionalFind(propertiesPattern: String): MatchResult? = Regex(propertiesPattern).find(this)
    }
}
