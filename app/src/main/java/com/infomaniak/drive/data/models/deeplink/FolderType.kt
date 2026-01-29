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


enum class FolderType(val type: String, val builder: (FileType) -> RoleFolder) {
    Collaboratives(type = "collaboratives", builder = RoleFolder::Collaboratives),
    Favorites(type = "favorites", builder = RoleFolder::Favorites),
    File(type = "file", builder = RoleFolder::File),
    MyShares(type = "my-shares", builder = RoleFolder::MyShare),
    SharedWithMe(type = "shared-with-me", builder = RoleFolder::SharedWithMe),
    SharedLinks(type = "shared-links", builder = RoleFolder::SharedLinks),
    Recent(type = "recent", builder = RoleFolder::Recent),
    Trash(type = "trash", builder = RoleFolder::Trash);

    companion object {
        const val GROUP_NAME_FILE = "file"
        const val FILE = "$FOLDER_ID/$FILE_ID"

        const val GROUP_NAME_PREVIEW = "preview"
        const val PREVIEW = "$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        const val GROUP_NAME_PREVIEW_FOLDER = "previewFolder"
        const val PREVIEW_FOLDER = "$FOLDER_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        const val SOURCE_DRIVE_FILE = "$DRIVE_ID/$FOLDER_ID/$FILE_ID"
        const val SOURCE_DRIVE_PREVIEW_FOLDER = "$DRIVE_ID/$FOLDER_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"

        /**
         * GENERIC_FOLDER_PROPERTIES filters this kind of paths :
         *      <folderId>/<fileId>
         *      preview/<type:string>/<fileId>
         *      <folderId>/preview/<type:string>/<fileId>
         *  It find one of them and assign it to a named group
         */
        const val GENERIC_FOLDER_PROPERTIES =
            "(?<$GROUP_NAME_FILE>$FILE)|(?<$GROUP_NAME_PREVIEW>$PREVIEW)|(?<$GROUP_NAME_PREVIEW_FOLDER>$PREVIEW_FOLDER)"

        /**
         * SHARED_WITH_ME_FOLDER_PROPERTIES filters this kind of paths :
         *       <sourceDriveId>/<folderId>/<fileId>
         *       <sourceDriveId>/<folderId>/preview/<type:string>/<fileId>
         *  It find one of them and assign it to a named group
         */
        const val SHARED_WITH_ME_FOLDER_PROPERTIES =
            "(?<$GROUP_NAME_FILE>$SOURCE_DRIVE_FILE)|(?<$GROUP_NAME_PREVIEW_FOLDER>$SOURCE_DRIVE_PREVIEW_FOLDER)"
        @Throws(InvalidValue::class)
        fun from(value: String): FolderType = entries.find { it.type == value } ?: throw InvalidValue()

        @Throws(InvalidValue::class)
        private fun MatchResult?.convertToDoubleDriveFileType(): FileType = this?.let {
            groups[GROUP_NAME_FILE]?.let { FileType.ExternalFile(this) }
                ?: groups[GROUP_NAME_PREVIEW_FOLDER]?.let { FileType.ExternalFilePreviewInFolder(this) }
        } ?: throw InvalidValue()

        @Throws(InvalidValue::class)
        private fun MatchResult?.convertToFileType(): FileType = this?.let {
            groups[GROUP_NAME_FILE]?.let { FileType.File(this) }
                ?: groups[GROUP_NAME_PREVIEW]?.let { FileType.FilePreview(this) }
                ?: groups[GROUP_NAME_PREVIEW_FOLDER]?.let { FileType.FilePreviewInFolder(this) }
        } ?: throw InvalidValue()

        fun FolderType.build(folderProperties: String): RoleFolder = when (this) {
            SharedWithMe -> Regex(SHARED_WITH_ME_FOLDER_PROPERTIES).find(folderProperties).convertToDoubleDriveFileType()
            else -> Regex(GENERIC_FOLDER_PROPERTIES).find(folderProperties).convertToFileType()
        }.let(builder)

    }
}
