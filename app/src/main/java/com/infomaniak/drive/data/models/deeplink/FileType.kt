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
            fileId = match.parseId(2),
        )

        companion object {
            const val PATTERN = "$FILE_ID$END_OF_REGEX"
        }
    }

    data class FilePreviewInFolder(val folderId: Int, override val fileId: Int) :
        FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            folderId = match.parseId(4),
            fileId = match.parseId(5),
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
         *      preview/<type:string>/<fileId>
         *      <folderId>/preview/<type:string>/<fileId>
         *  It find one of them and assign it to a named group
         *
         *  Order in this Regex implies index for each parsing in ExternalFileType constructors
         */
        val FOLDER_PROPERTIES = listOf(
            "(?<$GROUP_FILE>${File.PATTERN})",
            "(?<$GROUP_PREVIEW_IN_FOLDER>${FilePreviewInFolder.PATTERN})"
        ).joinToString(separator = "|")

        fun MatchResult?.extractFileType(): FileType =
            this?.let {
                groups[GROUP_FILE]?.let { File(this) }
                    ?: groups[GROUP_PREVIEW_IN_FOLDER]?.let { FilePreviewInFolder(this) }
            } ?: throw InvalidValue()
    }
}
