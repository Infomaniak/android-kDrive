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
import com.infomaniak.drive.data.models.File
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface ExternalFileType : Parcelable {
    val sourceDriveId: Int

    data class FilePreview(override val sourceDriveId: Int, val fileId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(GROUP_DRIVE_ID),
            fileId = match.parseId(GROUP_FILE_ID),
        )

        companion object {
            const val PATTERN = "$DRIVE_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        }
    }

    data class Folder(override val sourceDriveId: Int, val folderId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(GROUP_DRIVE_ID),
            folderId = match.parseId(GROUP_FOLDER_ID),
        )

        companion object {
            const val PATTERN = "$DRIVE_ID/$FOLDER_ID"
        }
    }

    data class FilePreviewInFolder(override val sourceDriveId: Int, val folderId: Int, val fileId: Int) : ExternalFileType {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(GROUP_DRIVE_ID),
            folderId = match.parseId(GROUP_FOLDER_ID),
            fileId = match.parseId(GROUP_FILE_ID),
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
        val SHARED_WITH_ME_FOLDER_PROPERTIES = arrayOf(
            "$START_OF_REGEX(?<$GROUP_PREVIEW_FILE>${FilePreview.PATTERN})$END_OF_REGEX",
            "$START_OF_REGEX(?<$GROUP_FOLDER>${Folder.PATTERN})$END_OF_REGEX",
            "$START_OF_REGEX(?<$GROUP_PREVIEW_FILE_IN_FOLDER>${FilePreviewInFolder.PATTERN})$END_OF_REGEX",
        )

        fun MatchResult?.extractExternalFileType(): ExternalFileType? {
            return tryMatchFor(GROUP_PREVIEW_FILE, ::FilePreview)
                ?: tryMatchFor(GROUP_FOLDER, ::Folder)
                ?: tryMatchFor(GROUP_PREVIEW_FILE_IN_FOLDER, ::FilePreviewInFolder)
        }

        fun fromFile(file: File): ExternalFileType {
            return with(file) {
                when {
                    isFolder() -> Folder(sourceDriveId = driveId, folderId = id)
                    parentId != 0 -> FilePreviewInFolder(driveId, parentId, id)
                    else -> FilePreview(sourceDriveId = driveId, fileId = id)
                }
            }
        }
    }
}
