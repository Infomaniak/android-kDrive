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
package com.infomaniak.drive.data.api.publicshare

import android.util.Log
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.api.ApiController.ApiMethod.GET
import com.infomaniak.core.network.api.ApiController.ApiMethod.POST
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.networking.HttpClient
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ApiRoutes.loadCursor
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.FileCount
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FileId
import okhttp3.OkHttpClient

object PublicShareApiRepository {

    suspend fun getPublicShareInfo(driveId: Int, linkUuid: String, authToken: String? = null): ApiResponse<ShareLink> {
        return callPublicShareApi(
            url = ApiRoutes.getPublicShareInfo(driveId, linkUuid),
            method = GET,
            authToken = authToken,
            okHttpClient = PublicShareHttpClient.okHttpClientWithTokenInterceptor,
        )
    }

    suspend fun submitPublicSharePassword(driveId: Int, linkUuid: String, password: String): ApiResponse<PublicShareToken> {
        return callPublicShareApi(
            url = ApiRoutes.submitPublicSharePassword(driveId, linkUuid),
            method = POST,
            body = mapOf("password" to password),
        )
    }

    suspend fun getPublicShareRootFile(
        driveId: Int,
        linkUuid: String,
        fileId: FileId,
        authToken: String? = null
    ): ApiResponse<File> {
        return callPublicShareApi(
            url = ApiRoutes.getPublicShareRootFile(driveId, linkUuid, fileId),
            method = GET,
            authToken = authToken,
        )
    }

    suspend fun getPublicShareChildrenFiles(
        driveId: Int,
        linkUuid: String,
        folderId: FileId,
        sortType: SortType,
        cursor: String?,
        authToken: String? = null,
    ): CursorApiResponse<List<File>> {
        val baseUrl = ApiRoutes.getPublicShareChildrenFiles(driveId, linkUuid, folderId, sortType)
        val authParam = authToken?.let { "&sharelink_token=$it" } ?: ""
        val url = baseUrl + authParam + "&${loadCursor(cursor)}"

        return callApiWithCursor(url, GET)
    }

    suspend fun getPublicShareFileCount(
        driveId: Int,
        linkUuid: String,
        fileId: Int,
        authToken: String? = null,
    ): ApiResponse<FileCount> {
        return callPublicShareApi(
            url = ApiRoutes.getPublicShareFileCount(driveId, linkUuid, fileId),
            method = GET,
            authToken = authToken,
        )
    }

    suspend fun buildPublicShareArchive(
        driveId: Int,
        linkUuid: String,
        archiveBody: ArchiveBody,
        authToken: String? = null
    ): ApiResponse<ArchiveUUID> {
        return callPublicShareApi(
            url = ApiRoutes.buildPublicShareArchive(driveId, linkUuid),
            method = POST,
            body = archiveBody,
            authToken = authToken,
        )
    }

    suspend fun importPublicShareFiles(
        sourceDriveId: Int,
        linkUuid: String,
        destinationUserId: Int,
        destinationDriveId: Int,
        destinationFolderId: Int,
        fileIds: List<Int>,
        exceptedFileIds: List<Int>,
        password: String = "",
        authToken: String? = null,
    ): ApiResponse<List<FileExternalImport>> {

        val body: MutableMap<String, Any> = mutableMapOf(
            "source_drive_id" to sourceDriveId,
            "sharelink_uuid" to linkUuid,
            "destination_folder_id" to destinationFolderId,
        )

        if (password.isNotBlank()) body["password"] = password
        if (fileIds.isNotEmpty()) body["file_ids"] = fileIds.toTypedArray()
        if (exceptedFileIds.isNotEmpty()) body["except_file_ids"] = exceptedFileIds.toTypedArray()

        return callPublicShareApi(
            url = ApiRoutes.importPublicShareFiles(destinationDriveId),
            method = POST,
            authToken = authToken,
            body = body,
            okHttpClient = AccountUtils.getHttpClient(
                userId = destinationUserId,
                getAuthenticator = null,
                getInterceptor = { tokenInterceptorListener -> PublicShareTokenInterceptor(tokenInterceptorListener) }
            ),
        )
    }

    private suspend inline fun <reified T> callPublicShareApi(
        url: String,
        method: ApiController.ApiMethod,
        authToken: String? = null,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): T {
        Log.e("TOTO", "callPublicShareApi: $authToken")
        val authParam = authToken?.let { token ->
            val paramPrefix = if (url.contains("?")) "&" else "?"
            "${paramPrefix}sharelink_token=$token"
        } ?: ""

        val authentifiedUrl = url + authParam
        Log.e("TOTO", "callPublicShareApi: $authentifiedUrl")
        return ApiController.callApi(authentifiedUrl, method, body, okHttpClient)
    }

    private suspend inline fun <reified T> callApiWithCursor(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
    ): T {
        return ApiController.callApi(
            url = url,
            method = method,
            body = body,
            okHttpClient = HttpClient.okHttpClient,
            buildErrorResult = { apiError, translatedErrorRes ->
                CursorApiResponse<Any>(
                    result = ApiResponseStatus.ERROR,
                    error = apiError
                ).apply {
                    translatedError = translatedErrorRes
                } as T
            }
        )
    }
}
