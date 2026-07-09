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

    fun loadFile(fileId: Int, callback: (File?) -> Unit) {
        viewModelScope.launch {
            currentFile = runCatching {
                FileController.getFileById(fileId, userDrive) ?: run {
                    withContext(Dispatchers.IO) {
                        val okHttpClient = AccountUtils.getHttpClient(userDrive.userId)
                        val remoteFile = FileController.getRemoteFile(fileId, userDrive.driveId, okHttpClient)
                        FileController.saveRemoteFileToDb(remoteFile!!, userDrive, okHttpClient)
                        remoteFile
                    }
                }
            }.getOrElse { _ ->
                null
            }
            callback(currentFile)
        }
    }

    private fun isOfflineFileComplete(offlineFile: IOFile?) = offlineFile?.let { currentFile?.isOfflineAndIntact(it) } ?: false
}
