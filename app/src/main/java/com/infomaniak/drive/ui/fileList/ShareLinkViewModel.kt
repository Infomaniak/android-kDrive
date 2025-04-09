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
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFilePermission
import kotlinx.coroutines.Dispatchers

class ShareLinkViewModel : ViewModel() {

    fun getShareLink(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.getShareLink(file))
    }

    fun createShareLink(file: File) = liveData(Dispatchers.IO) {
        val body = ShareLink().ShareLinkSettings(right = ShareLinkFilePermission.PUBLIC, canDownload = true, canEdit = false)
        val apiResponse = ApiRepository.createShareLink(file, body)

        if (apiResponse.isSuccess()) {
            FileController.updateFile(file.id) { it.shareLink = apiResponse.data }
            updateShareLinkDriveQuota(shouldIncrease = true)
        }
        emit(apiResponse)
    }

    fun deleteFileShareLink(file: File) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.deleteFileShareLink(file)
        if (apiResponse.isSuccess()) {
            FileController.updateFile(file.id) {
                it.shareLink = null
                it.rights?.canBecomeShareLink = true
            }
            updateShareLinkDriveQuota(shouldIncrease = false)
        }
        emit(apiResponse)
    }

    private fun updateShareLinkDriveQuota(shouldIncrease: Boolean) {
        DriveInfosController.updateDrive {
            it.quotas.sharedLink?.apply { if (shouldIncrease) current++ else current-- }
        }
    }
}
