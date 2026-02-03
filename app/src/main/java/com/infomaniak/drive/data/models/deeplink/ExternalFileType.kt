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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface ExternalFileType : Parcelable {
    val sourceDriveId: Int

    class FilePreview(override val sourceDriveId: Int, val fileId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(2),
            fileId = match.parseId(4),
        )

        companion object {
            const val PATTERN = "$DRIVE_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        }
    }

    class Folder(override val sourceDriveId: Int, val folderId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(6),
            folderId = match.parseId(7),
        )

        companion object {
            const val PATTERN = "$DRIVE_ID/$FOLDER_ID$END_OF_REGEX"
        }
    }

    class FilePreviewInFolder(override val sourceDriveId: Int, val folderId: Int, val fileId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(9),
            folderId = match.parseId(10),
            fileId = match.parseId(12),
        )

        companion object {
            const val PATTERN = "$DRIVE_ID/$FOLDER_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        }
    }

    companion object {

        private const val GROUP_PREVIEW_FILE = "file"
        private const val GROUP_FOLDER = "folder"
        private const val GROUP_PREVIEW_FILE_IN_FOLDER = "fileInFolder"
        /**
         * SHARED_WITH_ME_FOLDER_PROPERTIES filters this kind of paths :
         *       <sourceDriveId>/preview/<type:string>/<fileId>
         *       <sourceDriveId>/<folderId>
         *       <sourceDriveId>/<folderId>/preview/<type:string>/<fileId>
         *  It find one of them and assign it to a named group
         *
         *  Order in this Regex implies index for each parsing in ExternalFileType constructors
         */
        val SHARED_WITH_ME_FOLDER_PROPERTIES = listOf(
            "(?<$GROUP_PREVIEW_FILE>${FilePreview.PATTERN})",
            "(?<$GROUP_FOLDER>${Folder.PATTERN})",
            "(?<$GROUP_PREVIEW_FILE_IN_FOLDER>${FilePreviewInFolder.PATTERN})"
        ).joinToString(separator = "|")

        fun MatchResult?.extractExternalFileType(): ExternalFileType? = this?.run {
            groups[GROUP_PREVIEW_FILE]?.let { FilePreview(this) }
                ?: groups[GROUP_FOLDER]?.let { Folder(this) }
                ?: groups[GROUP_PREVIEW_FILE_IN_FOLDER]?.let { FilePreviewInFolder(this) }
        }
    }
}
