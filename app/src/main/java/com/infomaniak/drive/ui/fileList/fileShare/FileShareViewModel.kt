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
package com.infomaniak.drive.ui.fileList.fileShare

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import kotlinx.coroutines.Dispatchers

class FileShareViewModel : ViewModel() {
    val currentFile = MutableLiveData<File>()
    val availableShareableItems = MutableLiveData<List<Shareable>>()

    fun postFileShareCheck(file: File, body: Map<String, Any>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileShareCheck(file, body))
    }

    fun postFileShare(file: File, body: Map<String, Any?>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileShare(file, body))
    }

    fun editFileShareLink(file: File, shareLink: ShareLink) = liveData(Dispatchers.IO) {
        val body = mutableMapOf<String, Any?>(
            "permission" to shareLink.permission,
            "block_downloads" to shareLink.blockDownloads,
            "block_comments" to shareLink.blockComments,
            "block_information" to shareLink.blockInformation,
            "valid_until" to (shareLink.validUntil?.time?.let { it / 1_000L } ?: "")
        )

        if (shareLink.password.isNullOrBlank()) {
            body.remove("password")
            if (shareLink.permission == ShareLink.ShareLinkPermission.PASSWORD)
                body.remove("permission")
        } else
            body["password"] = shareLink.password

        emit(ApiRepository.putFileShareLink(file, body))
    }
}