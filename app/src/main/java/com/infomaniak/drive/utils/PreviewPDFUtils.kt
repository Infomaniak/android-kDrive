/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import com.google.gson.JsonParser
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream

object PreviewPDFUtils {
    private const val BUFFER_SIZE = 8192

    fun convertPdfFileToIOFile(
        context: Context,
        file: File,
        userDrive: UserDrive,
        onProgress: (progress: Int) -> Unit
    ): ApiResponse<IOFile> {
        return runCatching {
            val outputFile = when {
                file.isOnlyOfficePreview() -> file.getConvertedPdfCache(context, userDrive)
                file.isOffline -> file.getOfflineFile(context, userDrive.userId)!!
                else -> file.getCacheFile(context, userDrive)
            }

            val officePdfNeedDownload = file.isOnlyOfficePreview() && (outputFile.lastModified() / 1000) < file.lastModifiedAt
            val pdfNeedDownload = !file.isOnlyOfficePreview() && file.isObsoleteOrNotIntact(outputFile)

            if (officePdfNeedDownload || pdfNeedDownload) {
                downloadFile(outputFile, file, onProgress)
                outputFile.setLastModified(file.getLastModifiedInMilliSecond())
            }

            ApiResponse(ApiResponseStatus.SUCCESS, outputFile)
        }.getOrElse { exception ->
            exception.printStackTrace()
            val error = when (exception) {
                is PasswordProtectedException -> R.string.previewFileProtectedError
                else -> R.string.previewNoPreview
            }
            ApiResponse(
                result = ApiResponseStatus.ERROR,
                data = null,
                translatedError = error,
            )
        }
    }

    private fun downloadFile(
        externalOutputFile: IOFile,
        fileModel: File,
        onProgress: (progress: Int) -> Unit
    ) {
        if (externalOutputFile.exists()) externalOutputFile.delete()

        val downLoadUrl = ApiRoutes.downloadFile(fileModel) + if (fileModel.isOnlyOfficePreview()) "?as=pdf" else ""
        val request = Request.Builder().url(downLoadUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
        val downloadProgressInterceptor = DownloadOfflineFileManager.downloadProgressInterceptor(onProgress = onProgress)
        val response = HttpClient.okHttpClient.newBuilder()
            .addNetworkInterceptor(downloadProgressInterceptor)
            .build()
            .newCall(request)
            .execute()

        response.use {
            if (!it.isSuccessful) {
                val errorCode = JsonParser.parseString(it.body?.string()).asJsonObject.getAsJsonPrimitive("error").asString
                if (errorCode == "password_protected_error") throw PasswordProtectedException() else throw Exception("Download error")
            }
            when (it.body?.contentType()?.toString()) {
                "application/pdf" -> createTempPdfFile(it, externalOutputFile)
                else -> throw UnsupportedOperationException("File not supported")
            }
        }
    }

    private fun createTempPdfFile(response: Response, file: IOFile) {
        BufferedInputStream(response.body?.byteStream(), BUFFER_SIZE).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }

    private class PasswordProtectedException : Exception()
}
