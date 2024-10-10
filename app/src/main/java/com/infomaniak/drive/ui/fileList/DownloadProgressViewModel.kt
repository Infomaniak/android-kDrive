/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Response

class DownloadProgressViewModel : ViewModel() {

    val downloadProgressLiveData = MutableLiveData(0)
    val localFile = SingleLiveEvent<File>()

    fun getLocalFile(fileId: Int, userDrive: UserDrive) {
        localFile.value = FileController.getFileById(fileId, userDrive)
    }

    fun downloadFile(context: Context, file: File, userDrive: UserDrive) = viewModelScope.launch(Dispatchers.IO) {
        val outputFile = file.getStoredFile(context, userDrive)
        if (outputFile == null) {
            downloadProgressLiveData.postValue(null)
            return@launch
        }

        if (file.isObsoleteOrNotIntact(outputFile)) {
            runCatching {
                val apiResponse = DownloadOfflineFileManager.downloadFileResponse(
                    fileUrl = ApiRoutes.downloadFile(file),
                    downloadInterceptor = DownloadOfflineFileManager.downloadProgressInterceptor { progress ->
                        downloadProgressLiveData.postValue(progress)
                    }
                )

                if (apiResponse.isSuccessful) {
                    saveData(file, outputFile, apiResponse)
                } else {
                    downloadProgressLiveData.postValue(null)
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                downloadProgressLiveData.postValue(null)
            }
        } else {
            downloadProgressLiveData.postValue(PROGRESS_COMPLETE)
        }
    }

    private fun saveData(file: File, outputFile: IOFile, response: Response) {
        if (outputFile.exists()) outputFile.delete()
        DownloadOfflineFileManager.saveRemoteData(TAG, response, outputFile) {
            downloadProgressLiveData.postValue(PROGRESS_COMPLETE)
        }
        outputFile.setLastModified(file.getLastModifiedInMilliSecond())
    }

    companion object {
        const val PROGRESS_COMPLETE = 100
        private const val TAG = "DownloadProgressDialog"
    }
}
