/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.fileShare

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileShareViewModel : ViewModel() {

    val currentDriveResult = MutableLiveData<Drive>()
    val currentFile = MutableLiveData<File>()
    val availableShareableItems = MutableLiveData<List<Shareable>>()

    private val driveRealm = DriveInfosController.getRealmInstance()

    fun fetchCurrentFile(fileId: Int) = liveData(Dispatchers.IO) {
        emit(
            FileController.getFileById(fileId)
                ?: ApiRepository.getFileDetails(File(id = fileId, driveId = AccountUtils.currentDriveId)).data
        )
    }

    fun initCurrentDriveLiveData(driveId: Int) = viewModelScope.launch {
        val drive = DriveInfosController.getDrive(
            userId = AccountUtils.currentUserId,
            driveId = driveId,
            maintenance = false,
            customRealm = driveRealm,
        )
        currentDriveResult.postValue(drive?.freeze())
    }

    fun postFileShareCheck(file: File, body: Map<String, Any>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileShareCheck(file, body))
    }

    fun postFileShare(file: File, body: Map<String, Any?>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.addMultiAccess(file, body))
    }

    fun editFileShareLink(file: File, shareLink: ShareLink) = liveData(Dispatchers.IO) {
        with(ApiRepository.updateShareLink(file, shareLink.ShareLinkSettings().toJsonElement())) {
            if (data == true) FileController.updateShareLinkWithRemote(file.id)
            emit(this)
        }
    }
}
