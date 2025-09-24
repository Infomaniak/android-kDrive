/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ApiRoutes.loadCursor
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.api.publicshare.PublicShareHttpClient.publicShareHttpClient
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.FileCount
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.utils.FileId
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.api.ApiController.ApiMethod.GET
import com.infomaniak.lib.core.api.ApiController.ApiMethod.POST
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus

object PublicShareApiRepository {

    suspend fun getPublicShareInfo(driveId: Int, linkUuid: String): ApiResponse<ShareLink> {
        return callApi(
            url = ApiRoutes.getPublicShareInfo(driveId, linkUuid),
            method = GET,
        )
    }

    suspend fun submitPublicSharePassword(driveId: Int, linkUuid: String, password: String): ApiResponse<Boolean> {
        return callApi(
            url = ApiRoutes.submitPublicSharePassword(driveId, linkUuid),
            method = POST,
            body = mapOf("password" to password),
        )
    }

    suspend fun getPublicShareRootFile(driveId: Int, linkUuid: String, fileId: FileId): ApiResponse<File> {
        return callApi(
            url = ApiRoutes.getPublicShareRootFile(driveId, linkUuid, fileId),
            method = GET,
        )
    }

    suspend fun getPublicShareChildrenFiles(
        driveId: Int,
        linkUuid: String,
        folderId: FileId,
        sortType: SortType,
        cursor: String?,
    ): CursorApiResponse<List<File>> {
        val url = ApiRoutes.getPublicShareChildrenFiles(driveId, linkUuid, folderId, sortType) + "&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    suspend fun getPublicShareFileCount(driveId: Int, linkUuid: String, fileId: Int): ApiResponse<FileCount> {
        return callApi(
            url = ApiRoutes.getPublicShareFileCount(driveId, linkUuid, fileId),
            method = GET,
        )
    }

    suspend fun buildPublicShareArchive(driveId: Int, linkUuid: String, archiveBody: ArchiveBody): ApiResponse<ArchiveUUID> {
        return callApi(
            url = ApiRoutes.buildPublicShareArchive(driveId, linkUuid),
            method = POST,
            body = archiveBody,
        )
    }

    suspend fun importPublicShareFiles(
        sourceDriveId: Int,
        linkUuid: String,
        destinationDriveId: Int,
        destinationFolderId: Int,
        fileIds: List<Int>,
        exceptedFileIds: List<Int>,
        password: String = "",
    ): ApiResponse<List<FileExternalImport>> {
        val body: MutableMap<String, Any> = mutableMapOf(
            "source_drive_id" to sourceDriveId,
            "sharelink_uuid" to linkUuid,
            "destination_folder_id" to destinationFolderId,
        )

        if (password.isNotBlank()) body["password"] = password
        if (fileIds.isNotEmpty()) body["file_ids"] = fileIds.toTypedArray()
        if (exceptedFileIds.isNotEmpty()) body["except_file_ids"] = exceptedFileIds.toTypedArray()

        return callApi(
            url = ApiRoutes.importPublicShareFiles(destinationDriveId),
            method = POST,
            body = body,
        )
    }

    private suspend inline fun <reified T> callApi(url: String, method: ApiController.ApiMethod, body: Any? = null): T {
        return ApiController.callApi(url, method, body, publicShareHttpClient)
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
            okHttpClient = publicShareHttpClient,
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
