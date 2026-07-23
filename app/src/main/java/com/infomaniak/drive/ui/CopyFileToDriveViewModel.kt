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
package com.infomaniak.drive.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CopyFileToDriveViewModel(val savedStateHandle: SavedStateHandle) : ViewModel() {
    val sourceFile = MutableSharedFlow<File?>()
    val hasMultipleDrives = MutableStateFlow(false)

    private val _selectedDrive = MutableStateFlow<Drive?>(null)
    val selectedDrive = _selectedDrive.asStateFlow()

    private val userId: Int
        inline get() = savedStateHandle[CopyFileToDriveActivityArgs::userId.name] ?: -1

    private val sourceDriveId: Int
        inline get() = savedStateHandle[CopyFileToDriveActivityArgs::sourceDriveId.name] ?: -1

    private val fileId: Int
        inline get() = savedStateHandle[CopyFileToDriveActivityArgs::fileId.name] ?: -1

    private val folderId = MutableStateFlow<Int?>(null)

    val selectedFolderName: StateFlow<String?> = combine(_selectedDrive, folderId) { drive, folderId ->
        if (drive == null || folderId == null) null else getFolder(drive, folderId)?.name
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    val canCopy: StateFlow<Boolean> = combine(_selectedDrive, folderId) { drive, folderId ->
        drive != null && folderId != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    init {
        loadSourceFile()
        loadSelectedDrive()
    }

    fun onDriveSelected(drive: Drive) {
        if (_selectedDrive.value?.id != drive.id) folderId.value = null
        _selectedDrive.value = drive
    }

    fun onFolderSelected(folderId: Int) {
        this.folderId.value = folderId
    }

    fun getCopyDestination(): CopyDestination? {
        val drive = _selectedDrive.value ?: return null
        val folderId = folderId.value ?: return null

        return CopyDestination(folderId = folderId, folderName = selectedFolderName.value, driveId = drive.id)
    }

    private fun loadSourceFile() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (fileId == -1 || sourceDriveId == -1 || userId == -1) sourceFile.emit(null)

                val isSourceSharedWithMe = DriveInfosController.getDrive(driveId = sourceDriveId)?.sharedWithMe == true
                val sourceUserDrive = UserDrive(userId = userId, driveId = sourceDriveId, sharedWithMe = isSourceSharedWithMe)

                sourceFile.emit(FileController.getFileById(fileId, sourceUserDrive))
            }
        }
    }

    private fun loadSelectedDrive() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val eligibleDrives = DriveInfosController.getEligibleDestinationDrives(
                    userId = userId,
                    excludedDriveId = sourceDriveId,
                    sharedWithMe = false,
                )
                hasMultipleDrives.emit(eligibleDrives.size > 1)
                _selectedDrive.emit(eligibleDrives.firstOrNull())
            }
        }
    }

    private fun getFolder(drive: Drive, folderId: Int): File? {
        val userDrive = UserDrive(userId = userId, driveId = drive.id, sharedWithMe = drive.sharedWithMe)
        return FileController.getFileById(folderId, userDrive)
    }

    data class CopyDestination(val folderId: Int, val folderName: String?, val driveId: Int)
}
