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
sealed interface DeeplinkFolderRole : Parcelable {
    val isHandled: Boolean
        get() = true

    data class Category(val id: Int, val fileId: Int? = null) : DeeplinkFolderRole {
        override val isHandled: Boolean get() = false
    }

    data object Collaboratives : DeeplinkFolderRole {
        override val isHandled: Boolean get() = false
    }

    data class Files(val filePath: DeeplinkFilePath) : DeeplinkFolderRole

    data class Recents(val fileId: Int?) : DeeplinkFolderRole

    data class SharedWithMe(val externalFilePath: DeeplinkExternalFilePath?) : DeeplinkFolderRole

    data class SharedLinks(val fileId: Int?) : DeeplinkFolderRole {
        override val isHandled: Boolean get() = false
    }

    data class Favorites(val fileId: Int?) : DeeplinkFolderRole

    data class MyShares(val fileId: Int?) : DeeplinkFolderRole

    data class Trash(val folderId: Int?) : DeeplinkFolderRole

    companion object {
        @Throws(InvalidFormatting::class)
        fun from(folderType: String, folderProperties: String): DeeplinkFolderRole {
            return DeeplinkFolderType.from(folderType).build(folderProperties)
        }
    }
}
