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
package com.infomaniak.drive.ui.selectPermission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.gson.JsonObject
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.Shareable
import kotlinx.coroutines.Dispatchers

internal class SelectPermissionViewModel : ViewModel() {

    var currentFile: File? = null
    var currentPermission: Permission? = null

    fun editFileShareLinkOfficePermission(file: File, canEdit: Boolean) = liveData(Dispatchers.IO) {
        val body = JsonObject().apply { addProperty("can_edit", canEdit) }
        val apiResponse = ApiRepository.updateShareLink(file, body)
        if (apiResponse.data == true) FileController.updateShareLinkWithRemote(file.id)
        emit(apiResponse)

    }

    fun deleteFileShare(file: File, shareable: Shareable) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteFileShare(file, shareable))
    }

    fun editFileShare(file: File, shareableItem: Shareable, permission: Shareable.ShareablePermission) =
        liveData(Dispatchers.IO) {
            emit(ApiRepository.putFileShare(file, shareableItem, mapOf("right" to permission.apiValue)))
        }

    fun removeDriveUser(file: File, shareable: Shareable) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteDriveUser(file = file, shareableItem = shareable))
    }
}
