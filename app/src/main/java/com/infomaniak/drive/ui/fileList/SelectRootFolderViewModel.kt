/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectRootFolderViewModel : ViewModel() {

    private val loadFiles = MutableSharedFlow<Int>(replay = 1)

    private var userDrive: UserDrive? = null
    private val realm
        get() = FileController.getRealmInstance(userDrive)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentFiles: StateFlow<List<File>> = loadFiles.distinctUntilChanged().flatMapLatest { limit ->
        FileController.getRecentFolders(realm, limit)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getRecentFolders(userDrive: UserDrive, limit: Int) {
        this.userDrive = userDrive
        viewModelScope.launch {
            loadFiles.emit(limit)
        }
    }
}
