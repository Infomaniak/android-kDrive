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
package com.infomaniak.drive.ui.fileList

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.Utils.ROOT_ID
import kotlinx.parcelize.Parcelize

class SelectFolderViewModel : ViewModel() {
    var userDrive: UserDrive? = null
    var currentDrive: Drive? = null
    var disableSelectedFolderId: Int? = null
    var navigationRestrictions = FolderNavigationRestrictions()

    fun getFolderName(folderId: Int): String {
        val selectedFolderName = if (folderId == ROOT_ID) {
            currentDrive?.name
        } else {
            FileController.getFileById(folderId, userDrive)?.name
        }
        return selectedFolderName ?: "/"
    }
}

@Parcelize
data class FolderNavigationRestrictions(
    val disabledFolderIds: Set<Int> = emptySet(),
    val disabledParentFolderId: Int? = null,
    val exceptedFolderIds: Set<Int> = emptySet(),
) : Parcelable
