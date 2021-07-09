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
package com.infomaniak.drive.utils

import android.content.Context
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream

object PreviewPDFUtils {
    private const val BUFFER_SIZE = 8192

    fun convertPdfFileToPdfCore(
        context: Context,
        file: File,
        userDrive: UserDrive,
        onProgress: (progress: Int) -> Unit
    ): ApiResponse<PdfCore> {
        return try {
            val outputFile = when {
                file.isOnlyOfficePreview() -> file.getConvertedPdfCache(context, userDrive)
                file.isOffline -> file.getOfflineFile(context, userDrive.userId)!!
                else -> file.getCacheFile(context, userDrive)
            }

            val officePdfNeedDownload = file.isOnlyOfficePreview() && (outputFile.lastModified() / 1000) < file.lastModifiedAt
            val pdfNeedDownload = !file.isOnlyOfficePreview() && !file.isOfflineAndIntact(outputFile)

            if (officePdfNeedDownload || pdfNeedDownload) {
                downloadFile(outputFile, file, onProgress)
                outputFile.setLastModified(file.getLastModifiedInMilliSecond())
            }

            ApiResponse(ApiResponse.Status.SUCCESS, PdfCore(context, outputFile))
        } catch (e: Exception) {
            e.printStackTrace()
            ApiResponse(ApiResponse.Status.ERROR, null, translatedError = R.string.anErrorHasOccurred)
        }
    }

    @Throws(Exception::class)
    private fun downloadFile(
        externalOutputFile: java.io.File,
        fileModel: File,
        onProgress: (progress: Int) -> Unit
    ) {
        if (externalOutputFile.exists()) externalOutputFile.delete()

        val downLoadUrl = ApiRoutes.downloadFile(fileModel) + if (fileModel.isOnlyOfficePreview()) "?as=pdf" else ""
        val request = Request.Builder().url(downLoadUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()
        val response = HttpClient.okHttpClient.newBuilder()
            .addNetworkInterceptor(DownloadWorker.downloadProgressInterceptor(onProgress)).build()
            .newCall(request).execute()

        response.use {
            if (!it.isSuccessful) throw Exception("Download error ")
            when (it.body?.contentType()?.toString()) {
                "application/pdf" -> createTempPdfFile(it, externalOutputFile)
                else -> throw UnsupportedOperationException("File not supported")
            }
        }
    }

    @Throws(Exception::class)
    private fun createTempPdfFile(response: Response, file: java.io.File) {
        BufferedInputStream(response.body?.byteStream(), BUFFER_SIZE).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }
}