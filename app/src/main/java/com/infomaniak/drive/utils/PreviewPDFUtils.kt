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
package com.infomaniak.drive.utils

import android.content.Context
import com.infomaniak.core.legacy.models.ApiResponse
import com.infomaniak.core.legacy.models.ApiResponseStatus
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive

object PreviewPDFUtils {

    suspend fun convertPdfFileToIOFile(
        context: Context,
        file: File,
        userDrive: UserDrive,
        onProgress: (progress: Int) -> Unit,
    ): ApiResponse<IOFile> = runCatching {
        ApiResponse(ApiResponseStatus.SUCCESS, data = file.convertToIOFile(context, userDrive, shouldBePdf = true, onProgress))
    }.getOrElse { exception ->
        exception.printStackTrace()
        val error = when (exception) {
            is PasswordProtectedException -> R.string.previewFileProtectedError
            else -> R.string.previewNoPreview
        }
        ApiResponse(result = ApiResponseStatus.ERROR, data = null, translatedError = error)
    }

    class PasswordProtectedException : Exception()
}
