/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FolderFilesProvider
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType.ONLY_FROM_REMOTE
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectRootFolderViewModel : ViewModel() {

    private val recentFilesToLoadLimit = MutableSharedFlow<Int>(replay = 1)

    private var userDrive: UserDrive? = null
    private val realm by lazy { FileController.getRealmInstance(userDrive) }

    private var rootFilesJob: Job = Job()

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentFiles: StateFlow<List<File>> = recentFilesToLoadLimit.distinctUntilChanged().flatMapLatest { limit ->
        FileController.getRecentFolders(realm, limit)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getRecentFolders(userDrive: UserDrive, limit: Int) {
        this.userDrive = userDrive
        viewModelScope.launch {
            recentFilesToLoadLimit.emit(limit)
        }
    }

    fun loadRootFiles(userDrive: UserDrive) {
        rootFilesJob.cancel()
        rootFilesJob = viewModelScope.launch(Dispatchers.IO) {
            FolderFilesProvider.getFiles(
                FolderFilesProvider.FolderFilesProviderArgs(
                    folderId = Utils.ROOT_ID,
                    isFirstPage = true,
                    order = SortType.NAME_AZ,
                    sourceRestrictionType = ONLY_FROM_REMOTE,
                    userDrive = userDrive,
                    isSupportingFileActivities = false,
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        rootFilesJob.cancel()
        realm.close()
    }
}
