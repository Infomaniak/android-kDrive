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
sealed interface RoleFolder : Parcelable {
    val isHandled: Boolean
        get() = true

    data class Category(val id: Int, val fileId: Int?) : RoleFolder {
        override val isHandled: Boolean get() = false
    }

    data object Collaboratives : RoleFolder {
        override val isHandled: Boolean get() = false
    }

    data class Files(val fileType: FileType) : RoleFolder
    data class Recents(val fileId: Int?) : RoleFolder
    data class SharedWithMe(val fileType: ExternalFileType?) : RoleFolder
    data class SharedLinks(val fileId: Int?) : RoleFolder {
        override val isHandled: Boolean get() = false
    }

    data class Favorites(val fileId: Int?) : RoleFolder
    data class MyShares(val fileId: Int?) : RoleFolder
    data class Trash(val folderId: Int?) : RoleFolder

    companion object {
        @Throws(InvalidValue::class)
        fun from(folderType: String, folderProperties: String): RoleFolder =
            FolderType.from(folderType).build(folderProperties)
    }
}
