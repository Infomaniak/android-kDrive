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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    ): LiveData<ApiResponse<DropBox>> = liveData(Dispatchers.IO) {
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
        val data = buildJsonObject {
            put("email_when_finished", JsonPrimitive(newDropBox.newHasNotification))
            put("valid_until", newDropBox.newValidUntil?.time?.div(1_000L)?.let(::JsonPrimitive) ?: JsonNull)
            put("limit_file_size", newDropBox.newLimitFileSize?.let(::JsonPrimitive) ?: JsonNull)

            when {
                newDropBox.newPassword && !newDropBox.newPasswordValue.isNullOrBlank() ->
                    newDropBox.newPasswordValue?.let(::JsonPrimitive)?.let { put("password", it) }
                !newDropBox.newPassword -> put("password", JsonNull)
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
