/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.bottomSheetDialogs

import androidx.viewbinding.ViewBinding
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.IOFile

class FileSharedActionsBottomSheetDialog : BaseFileActionsBottomSheetDialog() {

    override val binding: ViewBinding
        get() = TODO("Not yet implemented")

    override lateinit var currentFile: File

    override fun shareFile() {
        TODO("Not yet implemented")
    }

    override fun saveToKDrive() {
        TODO("Not yet implemented")
    }

    override fun printClicked() {
        super.printClicked()
        // TODO
    }

    override fun downloadFileClicked() {
        super.downloadFileClicked()
        // TODO
    }

    override fun openWith() = Unit
    override fun displayInfoClicked() = Unit
    override fun fileRightsClicked() = Unit
    override fun goToFolder() = Unit
    override fun manageCategoriesClicked(fileId: Int) = Unit
    override fun onCacheAddedToOffline() = Unit
    override fun onDeleteFile(onApiResponse: () -> Unit) = Unit
    override fun onLeaveShare(onApiResponse: () -> Unit) = Unit
    override fun onDuplicateFile(destinationFolder: File) = Unit
    override fun onMoveFile(destinationFolder: File) = Unit
    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit
    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit
}