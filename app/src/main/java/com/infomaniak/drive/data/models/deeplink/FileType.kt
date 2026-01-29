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
    class File(val folderId: Int, override val fileId: Int) : FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            folderId = match.parseId(2),
            fileId = match.parseId(3),
        )
    }

    class FilePreview(val fileType: String, override val fileId: Int) : FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            fileType = match.groupValues[5],
            fileId = match.parseId(6),
        )
    }

    class FilePreviewInFolder(val folderId: Int, val fileType: String, override val fileId: Int) :
        FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            folderId = match.parseId(8),
            fileType = match.groupValues[9],
            fileId = match.parseId(10),
        )
    }

    class ExternalFile(val sourceDriveId: Int, val folderId: Int, override val fileId: Int) : FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(2),
            folderId = match.parseId(3),
            fileId = match.parseId(4),
        )
    }

    class ExternalFilePreviewInFolder(
        val sourceDriveId: Int,
        val folderId: Int,
        val fileType: String,
        override val fileId: Int
    ) :
        FileType(fileId = fileId) {
        constructor(match: MatchResult) : this(
            sourceDriveId = match.parseId(6),
            folderId = match.parseId(7),
            fileType = match.groupValues[8],
            fileId = match.parseId(9),
        )
    }

    companion object {
        private fun MatchResult.parseId(index: Int) = try {
            groupValues[index].toInt()
        } catch (_: Exception) {
            throw InvalidValue()
        }
    }
}
