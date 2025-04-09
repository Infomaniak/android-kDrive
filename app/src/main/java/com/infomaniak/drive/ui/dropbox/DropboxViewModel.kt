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
package com.infomaniak.drive.ui.dropbox

import androidx.collection.arrayMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers

class DropboxViewModel : ViewModel() {

    val createDropBoxSuccess = SingleLiveEvent<DropBox>()

    fun getDropBox(file: File) = liveData(Dispatchers.IO) {
        emit(ApiRepository.getDropBox(file))
    }

    fun createDropBoxFolder(
        file: File,
        emailWhenFinished: Boolean,
        limitFileSize: Long? = null,
        password: String? = null,
        validUntil: Long? = null
    ) = liveData(Dispatchers.IO) {
        val body = arrayMapOf(
            "email_when_finished" to emailWhenFinished,
            "limit_file_size" to limitFileSize,
            "password" to password
        )
        validUntil?.let { body.put("valid_until", validUntil) }

        with(ApiRepository.postDropBox(file, body)) {
            if (isSuccess()) {
                FileController.updateDropBox(file.id, data)
                updateDropboxDriveQuota(shouldIncrease = true)
            }
            emit(this)
        }
    }

    fun updateDropBox(file: File, newDropBox: DropBox) = liveData(Dispatchers.IO) {
        val data = JsonObject().apply {
            addProperty("email_when_finished", newDropBox.newHasNotification)
            addProperty("valid_until", newDropBox.newValidUntil?.time?.let { it / 1000 })
            addProperty("limit_file_size", newDropBox.newLimitFileSize)

            if (newDropBox.newPassword && !newDropBox.newPasswordValue.isNullOrBlank()) {
                addProperty("password", newDropBox.newPasswordValue)
            } else if (!newDropBox.newPassword) {
                val password: String? = null
                addProperty("password", password)
            }
        }
        with(ApiRepository.updateDropBox(file, data)) {
            if (isSuccess()) FileController.updateDropBox(file.id, newDropBox)
            emit(this)
        }
    }

    fun deleteDropBox(file: File) = liveData(Dispatchers.IO) {
        val apiResponse = ApiRepository.deleteDropBox(file)
        if (apiResponse.isSuccess()) updateDropboxDriveQuota(shouldIncrease = false)
        emit(apiResponse)
    }

    private fun updateDropboxDriveQuota(shouldIncrease: Boolean) {
        DriveInfosController.updateDrive {
            it.quotas.dropbox?.apply { if (shouldIncrease) current++ else current-- }
        }
    }
}
