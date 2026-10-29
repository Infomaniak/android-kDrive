/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.api

import androidx.collection.arrayMapOf
import com.infomaniak.core.auth.api.ApiRepositoryCore
import com.infomaniak.core.auth.networking.AuthHttpClientProvider
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.api.ApiController.ApiMethod.DELETE
import com.infomaniak.core.network.api.ApiController.ApiMethod.GET
import com.infomaniak.core.network.api.ApiController.ApiMethod.POST
import com.infomaniak.core.network.api.ApiController.ApiMethod.PUT
import com.infomaniak.core.network.api.ApiController.callApiBlocking
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.data.api.ApiRoutes.loadCursor
import com.infomaniak.drive.data.api.UploadTask.Companion.ConflictOption
import com.infomaniak.drive.data.models.ArchiveUUID
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.BulkOperation
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.FileCheckResult
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.data.models.FileCount
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.ShareableItems
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.models.file.FileLastActivityBody
import com.infomaniak.drive.data.models.file.LastFileAction
import com.infomaniak.drive.data.models.file.ListingFiles
import com.infomaniak.drive.data.models.upload.UploadSegment.ChunkStatus
import com.infomaniak.drive.data.models.upload.UploadSession
import com.infomaniak.drive.data.models.upload.UploadSession.StartSessionBody
import com.infomaniak.drive.data.models.upload.UploadSession.StartUploadSession
import com.infomaniak.drive.data.models.upload.ValidChunks
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.SerializationUtils
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import java.util.Date
import com.infomaniak.core.ksuite.myksuite.ui.network.ApiRoutes as MyKSuiteApiRoutes

object ApiRepository : ApiRepositoryCore() {

    private inline fun <reified T> callApi(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient,
        useKotlinxSerialization: Boolean = true,
    ): T {
        return callApiBlocking(url, method, body, okHttpClient, useKotlinxSerialization)
    }

    private suspend inline fun <reified T> callApiSuspend(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient,
        useKotlinxSerialization: Boolean = true,
    ): T = ApiController.callApi(url, method, body, okHttpClient, useKotlinxSerialization)

    private inline fun <reified T> callApiWithCursor(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient,
        useKotlinxSerialization: Boolean = true,
    ): T {
        return callApiBlocking(url, method, body, okHttpClient, useKotlinxSerialization, buildErrorResult = { apiError, translatedErrorRes ->
            CursorApiResponse<Any>(
                result = ApiResponseStatus.ERROR,
                error = apiError
            ).apply {
                translatedError = translatedErrorRes
            } as T
        })
    }

    private suspend inline fun <reified T> callApiWithCursorSuspend(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient,
        useKotlinxSerialization: Boolean = true,
    ): T {
        return ApiController.callApi(url, method, body, okHttpClient, useKotlinxSerialization, buildErrorResult = { apiError, translatedErrorRes ->
            CursorApiResponse<Any>(
                result = ApiResponseStatus.ERROR,
                error = apiError
            ).apply {
                translatedError = translatedErrorRes
            } as T
        })
    }

    fun getAllDrivesData(
        okHttpClient: OkHttpClient
    ): ApiResponse<DriveInfo> {
        val url = ApiRoutes.getAllDrivesData()
        return callApi(url, GET, okHttpClient = okHttpClient, useKotlinxSerialization = true)
    }

    fun getFavoriteFiles(
        driveId: Int,
        order: SortType,
        cursor: String?,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient
    ): CursorApiResponse<ArrayList<File>> {
        val url = ApiRoutes.getFavoriteFiles(driveId, order) + "&${loadCursor(cursor)}"
        return callApiWithCursor(
            url = url,
            method = GET,
            okHttpClient = okHttpClient,
        )
    }

    fun getSharedWithMeFiles(okHttpClient: OkHttpClient, order: SortType, cursor: String?): CursorApiResponse<List<File>> {
        return callApiWithCursor(
            url = "${ApiRoutes.getSharedWithMeFiles(order)}&${loadCursor(cursor)}",
            method = GET,
            okHttpClient = okHttpClient,
        )
    }

    fun postFavoriteFile(file: File): ApiResponse<Boolean> = callApi(
        ApiRoutes.favorite(file), POST,
        useKotlinxSerialization = true,
    )

    fun deleteFavoriteFile(file: File): ApiResponse<Boolean> = callApi(
        ApiRoutes.favorite(file), DELETE,
        useKotlinxSerialization = true,
    )

    fun getFolderFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        cursor: String? = null,
        order: SortType
    ): CursorApiResponse<List<File>> {
        val url = "${ApiRoutes.getFolderFiles(driveId, parentId, order)}&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    fun getListingFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        cursor: String? = null,
        order: SortType
    ): CursorApiResponse<ListingFiles> {
        SentryLog.i("ApiRepository", "getListingFiles with cursor ${cursor != null}")
        val url = when (cursor) {
            null -> "${ApiRoutes.getListingFiles(driveId, parentId, order)}&${loadCursor(cursor)}"
            else -> "${ApiRoutes.getMoreListingFiles(driveId, parentId, order)}&${loadCursor(cursor)}"
        }
        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    // Increase timeout for this API call because it can take more than 10s to process data
    fun getFileActivities(
        file: File,
        cursor: String?,
        forFileList: Boolean,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClientLongTimeout,
    ): CursorApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getFileActivities(file, forFileList, loadCursor(cursor))
        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    fun getFilesLastActivities(driveId: Int, body: FileLastActivityBody): ApiResponse<List<LastFileAction>> {
        val url = "${ApiRoutes.getFilesLastActivities(driveId)}?with=file"
        return callApi(url, POST, SerializationUtils.encodeBody(body))
    }

    // For sync offline service
    fun getFileActivities(
        driveId: Int,
        fileIds: List<Int>,
        fromDate: Long,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClientLongTimeout,
    ): ApiResponse<ArrayList<FileActivity>> {
        val formattedFileIds = fileIds.joinToString(",") { it.toString() }
        return callApi(ApiRoutes.getFileActivities(driveId, formattedFileIds, fromDate), GET, okHttpClient = okHttpClient)
    }

    fun getLastModifiedFiles(driveId: Int, cursor: String? = null): CursorApiResponse<ArrayList<File>> {
        val url = "${ApiRoutes.getLastModifiedFiles(driveId)}&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun getLastGallery(driveId: Int, cursor: String?): CursorApiResponse<ArrayList<File>> {
        val types = "&types[]=${ExtensionType.IMAGE.value}&types[]=${ExtensionType.VIDEO.value}"
        val url = "${ApiRoutes.searchFiles(driveId, SortType.RECENT)}$types&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun getValidChunks(driveId: Int, uploadToken: String, okHttpClient: OkHttpClient): ApiResponse<ValidChunks> {
        val url = "${ApiRoutes.getSession(driveId, uploadToken)}?status[]=${ChunkStatus.OK}&with=chunks"
        return callApi(url, GET, okHttpClient = okHttpClient, useKotlinxSerialization = true)
    }

    fun startUploadSession(driveId: Int, body: StartSessionBody, okHttpClient: OkHttpClient): ApiResponse<StartUploadSession> {
        return callApi(ApiRoutes.startUploadSession(driveId), POST, SerializationUtils.encodeBody(body), okHttpClient = okHttpClient)
    }

    fun finishSession(driveId: Int, uploadToken: String, okHttpClient: OkHttpClient): ApiResponse<UploadSession> {
        return callApi(ApiRoutes.closeSession(driveId, uploadToken), POST, okHttpClient = okHttpClient)
    }

    fun cancelSession(driveId: Int, uploadToken: String, okHttpClient: OkHttpClient): ApiResponse<Boolean> {
        return callApi(
            ApiRoutes.getSession(driveId, uploadToken), DELETE, okHttpClient = okHttpClient,
            useKotlinxSerialization = true,
        )
    }

    suspend fun uploadEmptyFile(uploadFile: UploadFile) = with(uploadFile) {
        val uploadUrl = ApiRoutes.uploadFileDirectlyUrl(
            driveId = driveId,
            directoryId = remoteFolder,
            fileName = fileName,
            fileSize = 0L,
            conflictOption = ConflictOption.RENAME,
            directoryPath = remoteSubFolder,
            createdAt = uploadFile.fileCreatedAt,
            lastModifiedAt = Date(uploadFile.getLastModified()),
        )

        ApiController.callApi<ApiResponse<File>>(
            uploadUrl,
            POST,
            okHttpClient = AuthHttpClientProvider.authOkHttpClient,
            useKotlinxSerialization = true,
        )
    }

    fun createFolder(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        name: String,
        onlyForMe: Boolean = false,
    ): ApiResponse<File> {
        return callApi(
            ApiRoutes.createFolder(driveId, parentId),
            POST,
            mapOf("name" to name, "only_for_me" to onlyForMe),
            okHttpClient = okHttpClient
        )
    }

    fun createOfficeFile(driveId: Int, folderId: Int, createFile: CreateFile): ApiResponse<File> {
        return callApi(ApiRoutes.createOfficeFile(driveId, folderId), POST, SerializationUtils.encodeBody(createFile))
    }

    fun createTeamFolder(
        okHttpClient: OkHttpClient,
        driveId: Int,
        name: String,
        forAllUsers: Boolean = false
    ): ApiResponse<File> {
        val body = mapOf("name" to name, "for_all_user" to forAllUsers)
        return callApi(ApiRoutes.createTeamFolder(driveId), POST, body, okHttpClient = okHttpClient)
    }

    fun searchFiles(
        driveId: Int,
        query: String? = null,
        sortType: SortType,
        cursor: String?,
        date: Pair<String, String>? = null,
        type: String? = null,
        categories: String? = null,
        okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient
    ): CursorApiResponse<ArrayList<File>> {
        var url = "${ApiRoutes.searchFiles(driveId, sortType)}&${loadCursor(cursor)}"
        if (!query.isNullOrBlank()) url += "&query=$query"
        if (date != null) url += "&modified_at=custom&modified_after=${date.first}&modified_before=${date.second}"
        if (type != null) url += "&types[]=$type"
        if (categories != null) url += "&category=$categories"

        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    fun deleteFile(file: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.fileURLV2(file), DELETE, useKotlinxSerialization = true)
    }

    fun renameFile(file: File, newName: String): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.renameFile(file), POST, mapOf("name" to newName), useKotlinxSerialization = true)
    }

    fun updateFolderColor(file: File, color: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateFolderColor(file), POST, mapOf("color" to color), useKotlinxSerialization = true)
    }

    fun duplicateFile(file: File, destinationId: Int): ApiResponse<File> {
        return callApi(ApiRoutes.duplicateFile(file, destinationId), POST)
    }

    fun copyFileToAnotherDrive(
        sourceDriveId: Int,
        sourcePath: String,
        destinationDriveId: Int,
        destinationFolderId: Int,
    ): ApiResponse<List<FileExternalImport>> {
        val body = mapOf(
            "application_drive_id" to sourceDriveId.toString(),
            "directory_id" to destinationFolderId,
            "source_path" to sourcePath,
        )
        val diagnostics = CopyToDriveDiagnostics()
        val client = AuthHttpClientProvider.authOkHttpClient.newBuilder().apply {
            interceptors().add(0, diagnostics)
        }.build()
        SentryLog.i(CopyToDriveDiagnostics.TAG, "stage=request_started")
        return callApi<ApiResponse<List<FileExternalImport>>>(
            ApiRoutes.importKDrive(destinationDriveId), POST, body, okHttpClient = client,
        ).also(diagnostics::report)
    }

    fun moveFile(file: File, newParent: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.moveFile(file, newParent.id), POST, useKotlinxSerialization = true)
    }

    fun getFileShare(okHttpClient: OkHttpClient, file: File): ApiResponse<Share> {
        return callApi(ApiRoutes.getFileShare(file), GET, okHttpClient = okHttpClient, useKotlinxSerialization = true)
    }

    fun getFileDetails(file: File, okHttpClient: OkHttpClient = AuthHttpClientProvider.authOkHttpClient): ApiResponse<File> {
        return callApi(ApiRoutes.getFileDetails(file), GET, okHttpClient = okHttpClient)
    }

    fun getFileCount(file: File): ApiResponse<FileCount> {
        return callApi(ApiRoutes.getFileCount(file), GET, useKotlinxSerialization = true)
    }

    fun getFileComments(file: File, cursor: String?): CursorApiResponse<ArrayList<FileComment>> {
        val url = "${ApiRoutes.fileComments(file)}&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET, useKotlinxSerialization = true)
    }

    fun postFileComment(file: File, body: String): ApiResponse<FileComment> {
        return callApi(ApiRoutes.fileComments(file), POST, mapOf("body" to body), useKotlinxSerialization = true)
    }

    fun answerComment(file: File, commentId: Int, body: String): ApiResponse<FileComment> {
        return callApi(
            ApiRoutes.answerComment(file, commentId),
            POST,
            mapOf("body" to body),
            useKotlinxSerialization = true
        )
    }

    fun putFileComment(file: File, commentId: Int, body: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileComment(file, commentId), PUT, mapOf("body" to body), useKotlinxSerialization = true)
    }

    fun deleteFileComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileComment(file, commentId), DELETE, useKotlinxSerialization = true)
    }

    fun postLikeComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.likeComment(file, commentId), POST, useKotlinxSerialization = true)
    }

    fun postUnlikeComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.unLikeComment(file, commentId), POST, useKotlinxSerialization = true)
    }

    fun getShareLink(file: File): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.shareLink(file), GET, useKotlinxSerialization = true)
    }

    fun createShareLink(file: File, body: ShareLink.ShareLinkSettings): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.shareLink(file), POST, body.toJsonElement(), useKotlinxSerialization = true)
    }

    fun updateShareLink(file: File, body: JsonElement): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), PUT, body, useKotlinxSerialization = true)
    }

    fun deleteFileShareLink(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), DELETE, useKotlinxSerialization = true)
    }

    fun postFileShareCheck(file: File, body: Map<String, Any>): ApiResponse<ArrayList<FileCheckResult>> {
        return callApi(ApiRoutes.checkFileShare(file), POST, body, useKotlinxSerialization = true)
    }

    fun addMultiAccess(file: File, body: Map<String, Any?>): ApiResponse<ShareableItems> {
        return callApi(ApiRoutes.accessUrl(file), POST, body, useKotlinxSerialization = true)
    }

    fun deleteFileShare(file: File, shareableItem: Shareable): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.teamAccess(file, shareableItem.id)
                is Invitation -> ApiRoutes.fileInvitationAccess(file, shareableItem.id)
                else -> ApiRoutes.userAccess(file, shareableItem.id)
            }, DELETE,
            useKotlinxSerialization = true,
        )
    }

    fun deleteDriveUser(file: File, shareableItem: Shareable): ApiResponse<Boolean> {
        return callApi(
            url = ApiRoutes.driveUser(driveId = file.driveId, driveUserId = shareableItem.id),
            method = DELETE,
            useKotlinxSerialization = true,
        )
    }

    fun putFileShare(file: File, shareableItem: Shareable, body: Map<String, String>): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.teamAccess(file, shareableItem.id)
                is Invitation -> ApiRoutes.fileInvitationAccess(file, shareableItem.id)
                else -> ApiRoutes.userAccess(file, shareableItem.id)
            }, PUT, body,
            useKotlinxSerialization = true,
        )
    }

    fun createCategory(driveId: Int, name: String, color: String): ApiResponse<Category> {
        val body = mapOf("name" to name, "color" to color)
        return callApi(ApiRoutes.categories(driveId), POST, body, useKotlinxSerialization = true)
    }

    fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): ApiResponse<Category> {
        val body = arrayMapOf("color" to color).apply {
            name?.let { put("name", it) }
        }
        return callApi(ApiRoutes.category(driveId, categoryId), PUT, body, useKotlinxSerialization = true)
    }

    fun deleteCategory(driveId: Int, categoryId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.category(driveId, categoryId), DELETE, useKotlinxSerialization = true)
    }

    fun addCategory(file: File, categoryId: Int): ApiResponse<ShareableItems.FeedbackAccessResource<Int, Unit>> {
        return callApi(ApiRoutes.fileCategory(file, categoryId), POST, useKotlinxSerialization = true)
    }

    fun addCategory(files: List<File>, categoryId: Int): ApiResponse<List<ShareableItems.FeedbackAccessResource<Int, Unit>>> {
        val driveId = files.firstOrNull()?.driveId ?: AccountUtils.currentDriveId
        return callApi(
            ApiRoutes.fileCategory(driveId, categoryId), POST, mapOf("file_ids" to files.map { it.id }),
            useKotlinxSerialization = true,
        )
    }

    fun removeCategory(file: File, categoryId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileCategory(file, categoryId), DELETE, useKotlinxSerialization = true)
    }

    fun removeCategory(
        files: List<File>,
        categoryId: Int,
    ): ApiResponse<List<ShareableItems.FeedbackAccessResource<Int, Unit>>> {
        val driveId = files.firstOrNull()?.driveId ?: AccountUtils.currentDriveId
        return callApi(
            ApiRoutes.fileCategory(driveId, categoryId), DELETE, mapOf("file_ids" to files.map { it.id }),
            useKotlinxSerialization = true,
        )
    }

    fun getLastActivities(driveId: Int, cursor: String?): CursorApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getLastActivities(driveId) + "&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun forceFolderAccess(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.forceFolderAccess(file), POST, useKotlinxSerialization = true)
    }

    fun getDropBox(file: File): ApiResponse<DropBox> {
        return callApi(ApiRoutes.dropBox(file), GET, useKotlinxSerialization = true)
    }

    fun updateDropBox(file: File, body: JsonElement): ApiResponse<Boolean> {
        return callApi(ApiRoutes.dropBox(file), PUT, body, useKotlinxSerialization = true)
    }

    fun postDropBox(file: File, body: Map<String, Any?>): ApiResponse<DropBox> {
        return callApi(ApiRoutes.dropBox(file), POST, body, useKotlinxSerialization = true)
    }

    fun deleteDropBox(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.dropBox(file), DELETE, useKotlinxSerialization = true)
    }

    fun convertFile(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.convertFile(file), POST)
    }

    suspend fun getDriveTrash(driveId: Int, order: SortType, cursor: String?): CursorApiResponse<List<File>> {
        return callApiWithCursorSuspend("${ApiRoutes.driveTrash(driveId, order)}&${loadCursor(cursor)}", GET)
    }

    fun getTrashedFile(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.trashedFile(file), GET)
    }

    suspend fun getTrashedFolderFiles(file: File, order: SortType, cursor: String?): CursorApiResponse<List<File>> {
        return callApiWithCursorSuspend("${ApiRoutes.trashedFolderFiles(file, order)}&${loadCursor(cursor)}", GET)
    }

    fun postRestoreTrashFile(file: File, body: Map<String, Int>?): ApiResponse<JsonElement> =
        callApi(ApiRoutes.restoreTrashFile(file), POST, body)

    fun emptyTrash(driveId: Int): ApiResponse<Boolean> =
        callApi(
            url = ApiRoutes.emptyTrash(driveId),
            method = DELETE,
            okHttpClient = AuthHttpClientProvider.authOkHttpClientLongTimeout,
            useKotlinxSerialization = true,
        )

    fun deleteTrashFile(file: File): ApiResponse<Boolean> = callApi(
        ApiRoutes.trashURLV2(file), DELETE,
        useKotlinxSerialization = true,
    )

    fun getMySharedFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        sortType: SortType,
        cursor: String?
    ): CursorApiResponse<ArrayList<File>> {
        return callApiWithCursor(
            url = "${ApiRoutes.getMySharedFiles(driveId, sortType)}&${loadCursor(cursor)}",
            method = GET,
            okHttpClient = okHttpClient,
        )
    }

    fun undoAction(action: CancellableAction): ApiResponse<Boolean> {
        return callApi(
            ApiRoutes.undoAction(action.driveId), POST, mapOf("cancel_id" to action.cancelId),
            useKotlinxSerialization = true,
        )
    }

    fun performCancellableBulkOperation(bulkOperation: BulkOperation): ApiResponse<CancellableAction> {
        return callApi(
            ApiRoutes.bulkAction(bulkOperation.parent.driveId), POST, bulkOperation.toMap(),
            useKotlinxSerialization = true,
        )
    }

    fun buildArchive(driveId: Int, archiveBody: ArchiveBody): ApiResponse<ArchiveUUID> {
        return callApi(ApiRoutes.buildArchive(driveId), POST, SerializationUtils.encodeBody(archiveBody))
    }

    fun cancelExternalImport(driveId: Int, importId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.cancelExternalImport(driveId, importId), PUT, useKotlinxSerialization = true)
    }

    suspend fun getMyKSuiteData(okHttpClient: OkHttpClient): ApiResponse<MyKSuiteData> {
        return ApiController.callApi(
            url = MyKSuiteApiRoutes.myKSuiteData(),
            method = GET,
            okHttpClient = okHttpClient,
            useKotlinxSerialization = true,
        )
    }
}
