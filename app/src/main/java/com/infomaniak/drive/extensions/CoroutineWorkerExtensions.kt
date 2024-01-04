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
package com.infomaniak.drive.extensions

import androidx.work.CoroutineWorker
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.BulkDownloadWorker
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse

fun CoroutineWorker.getFileFromRemote(
    fileId: Int,
    userDrive: UserDrive = UserDrive(),
    onFileDownloaded: (downloadedFile: File) -> Unit
) {
    val fileDetails = ApiRepository.getFileDetails(File(id = fileId, driveId = userDrive.driveId))
    val remoteFile = fileDetails.data
    val file = if (fileDetails.isSuccess() && remoteFile != null) {
        FileController.getRealmInstance(userDrive).use { realm ->
            FileController.updateExistingFile(newFile = remoteFile, realm = realm)
        }
        remoteFile
    } else {
        if (fileDetails.error?.exception is ApiController.NetworkException) throw UploadTask.NetworkException()

        val translatedError = fileDetails.translatedError
        val responseGsonType = object : TypeToken<ApiResponse<File>>() {}.type
        val translatedErrorText = if (translatedError == 0) "" else applicationContext.getString(translatedError)
        val responseJson = ApiController.gson.toJson(fileDetails, responseGsonType)
        throw BulkDownloadWorker.RemoteFileException("$responseJson $translatedErrorText")
    }
    onFileDownloaded.invoke(file)
}
