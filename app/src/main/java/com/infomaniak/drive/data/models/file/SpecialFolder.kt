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
package com.infomaniak.drive.data.models.file

import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Type

sealed class SpecialFolder(id: Int, name: String, onFileInit: (File.() -> Unit)? = null) {

    val file by lazy {
        File(id = id, name = name).apply {
            initUid()
            onFileInit?.invoke(this)
        }
    }
    val id: Int
        get() = file.id

    data object Favorites : SpecialFolder(
        id = FAVORITES_FILE_ID,
        name = "Favorites"
    )

    data object MyShares : SpecialFolder(
        id = MY_SHARES_FILE_ID,
        name = "My Shares"
    )

    data object Gallery : SpecialFolder(
        id = GALLERY_FILE_ID,
        name = "Gallery"
    )

    data object RecentChanges : SpecialFolder(
        id = RECENT_CHANGES_FILE_ID,
        name = "Recent changes"
    )

    data object SharedWithMe : SpecialFolder(
        id = SHARED_WITH_ME_FILE_ID,
        name = "Shared with me"
    )

    data object Trash : SpecialFolder(
        id = TRASH_FILE_ID,
        name = "Trash",
        onFileInit = {
            status = "trash"
            type = Type.DIRECTORY.value
        }
    )

    companion object {
        private const val FAVORITES_FILE_ID = -1
        private const val MY_SHARES_FILE_ID = -2
        private const val GALLERY_FILE_ID = -3
        private const val RECENT_CHANGES_FILE_ID = -4
        private const val SHARED_WITH_ME_FILE_ID = -5
        private const val TRASH_FILE_ID = -6
    }
}
