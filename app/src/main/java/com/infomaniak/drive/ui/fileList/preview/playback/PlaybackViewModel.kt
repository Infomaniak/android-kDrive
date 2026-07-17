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
package com.infomaniak.drive.ui.fileList.preview.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.data.api.publicshare.PublicShareApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    val offlineFile: IOFile? by lazy {
        if (currentFile?.isOffline == true) {
            currentFile?.getOfflineFile(getApplication(), userDrive.userId)
        } else {
            null
        }
    }

    val offlineIsComplete by lazy { isOfflineFileComplete(offlineFile) }

    var currentFile: File? = null

    private val userDrive by lazy { UserDrive() }

    fun loadFile(
        fileId: Int,
        driveId: Int,
        publicShareUuid: String?,
        publicShareAuthToken: String?,
        callback: (File?) -> Unit,
    ) {
        viewModelScope.launch {
            currentFile = if (publicShareUuid.isNullOrBlank()) {
                loadDriveFile(fileId)
            } else {
                loadPublicShareFile(fileId, driveId, publicShareUuid, publicShareAuthToken)
            }
            callback(currentFile)
        }
    }

    private suspend fun loadDriveFile(fileId: Int): File? = runCatching {
        FileController.getFileByUidOrId(fileId, userDrive) ?: withContext(Dispatchers.IO) {
            val okHttpClient = AccountUtils.getHttpClient(userDrive.userId)
            FileController.getRemoteFile(fileId, userDrive.driveId, okHttpClient)?.also { remoteFile ->
                FileController.saveRemoteFileToDb(remoteFile, userDrive, okHttpClient)
            }
        }
    }.onFailure { exception ->
        SentryLog.e(TAG, "Failed to load drive file", exception)
    }.getOrNull()

    private suspend fun loadPublicShareFile(
        fileId: Int,
        driveId: Int,
        publicShareUuid: String,
        publicShareAuthToken: String?,
    ): File? {
        val response = PublicShareApiRepository.getPublicShareRootFile(
            driveId = driveId,
            linkUuid = publicShareUuid,
            fileId = fileId,
            authToken = publicShareAuthToken,
        )
        if (!response.isSuccess()) {
            SentryLog.e(TAG, "Failed to load public share file: ${response.error?.code}")
        }
        return response.data?.apply {
            this.publicShareUuid = publicShareUuid
            this.publicShareAuthToken = publicShareAuthToken
        }
    }

    private fun isOfflineFileComplete(offlineFile: IOFile?) = offlineFile?.let { currentFile?.isOfflineAndIntact(it) } ?: false

    companion object {
        private const val TAG = "PlaybackViewModel"
    }
}
