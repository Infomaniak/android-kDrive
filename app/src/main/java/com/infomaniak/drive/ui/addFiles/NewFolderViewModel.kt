/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
package com.infomaniak.drive.ui.addFiles

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import kotlinx.coroutines.Dispatchers

class NewFolderViewModel : ViewModel() {

    val currentFolderId = MutableLiveData<Int>()
    var userDrive: UserDrive? = null

    fun createFolder(name: String, parentId: Int, onlyForMe: Boolean) = liveData(Dispatchers.IO) {
        emit(FileController.createFolder(name, parentId, onlyForMe, userDrive))
    }

    fun createCommonFolder(name: String, forAllUsers: Boolean) = liveData(Dispatchers.IO) {
        emit(FileController.createCommonFolder(name, forAllUsers, userDrive))
    }

    fun saveNewFolder(parentFolderId: Int, newFolder: File) {
        FileController.saveNewFolder(parentFolderId, newFolder, userDrive)
    }
}