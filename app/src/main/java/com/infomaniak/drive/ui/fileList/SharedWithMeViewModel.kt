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
package com.infomaniak.drive.ui.fileList

import androidx.lifecycle.*
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FolderFilesProvider
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.Utils
import io.realm.kotlin.toFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SharedWithMeViewModel : ViewModel() {

    val sharedWithMeRealm = FileController.getRealmInstance(UserDrive(sharedWithMe = true))

    private var sharedWithMeJob: Job? = null

    private val loadSharedWithMeFiles = MutableLiveData<Pair<Int, File.SortType>>()
    val sharedWithMeFiles = loadSharedWithMeFiles.switchMap { (folderId, order) ->
        FileController.getRealmLiveFiles(folderId, sharedWithMeRealm, order).toFlow().asLiveData()
    }

    fun loadSharedWithMeFiles(
        parentId: Int,
        order: File.SortType,
        userDrive: UserDrive,
        isNewSort: Boolean,
    ) {
        sharedWithMeJob?.cancel()
        sharedWithMeJob = viewModelScope.launch(Dispatchers.IO) {
            val folderId = if (parentId == Utils.ROOT_ID) FileController.SHARED_WITH_ME_FILE_ID else parentId
            var dataNotAlreadyLoaded = true

            fun notifyUiToLoadData() {
                loadSharedWithMeFiles.postValue(folderId to order)
                dataNotAlreadyLoaded = false
            }

            if (parentId == Utils.ROOT_ID) {
                FileController.createSharedWithMeFolderIfNeeded(userDrive)
            }

            val folderIsNotEmpty = FileController.getFileById(folderId, userDrive)?.children?.isNotEmpty() == true
            if (folderIsNotEmpty) notifyUiToLoadData()

            if (!isNewSort) {
                FolderFilesProvider.loadSharedWithMeFiles(
                    folderFilesProviderArgs = FolderFilesProvider.FolderFilesProviderArgs(
                        folderId = parentId,
                        order = order,
                        userDrive = userDrive,
                    ),
                    onRecursionStart = {
                        // Notify the first page is already loaded
                        if (dataNotAlreadyLoaded) notifyUiToLoadData()
                    }
                )
                // Notify finish with an error or success without recursion
                if (dataNotAlreadyLoaded) notifyUiToLoadData()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { sharedWithMeRealm.close() }
        sharedWithMeJob?.cancel()
    }
}
