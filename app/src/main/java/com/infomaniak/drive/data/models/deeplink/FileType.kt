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
sealed class FileType(open val fileId: Int) : Parcelable {
    data class File(override val fileId: Int) : FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            fileId = match.parseId(GROUP_FILE_ID),
        )

        companion object {
            const val PATTERN = FILE_ID
        }
    }

    data class FilePreviewInFolder(val folderId: Int, override val fileId: Int) :
        FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            folderId = match.parseId(GROUP_FOLDER_ID),
            fileId = match.parseId(GROUP_FILE_ID),
        )

        companion object {
            const val PATTERN = "$FOLDER_ID/$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
        }
    }

    companion object {
        const val GROUP_FILE = "file"
        const val GROUP_PREVIEW_IN_FOLDER = "previewFolder"

        /**
         * FOLDER_PROPERTIES filters this kind of paths :
         *      <folderId>/<fileId>
         *      <folderId>/preview/<type:string>/<fileId>
         *  It find one of them and assign it to a named group
         *
         *  Order in this Regex implies index for each parsing in ExternalFileType constructors
         */
        val FOLDER_PROPERTIES = arrayOf(
            "$START_OF_REGEX(?<$GROUP_FILE>${File.PATTERN})$END_OF_REGEX",
            "$START_OF_REGEX(?<$GROUP_PREVIEW_IN_FOLDER>${FilePreviewInFolder.PATTERN})$END_OF_REGEX",
        )

        fun MatchResult?.extractFileType(): FileType {
            return tryMatchFor(GROUP_FILE, ::File)
                ?: tryMatchFor(GROUP_PREVIEW_IN_FOLDER, ::FilePreviewInFolder)
                ?: throw InvalidFormatting()
        }
    }
}
