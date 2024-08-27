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
package com.infomaniak.drive.data.api

import androidx.collection.arrayMapOf
import com.google.gson.JsonElement
import com.infomaniak.drive.data.api.UploadTask.Companion.ConflictOption
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.File.SortType
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
import com.infomaniak.drive.utils.FileId
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.api.ApiController.ApiMethod.*
import com.infomaniak.lib.core.api.ApiController.callApi
import com.infomaniak.lib.core.api.ApiRepositoryCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.networking.HttpClient
import okhttp3.OkHttpClient

object ApiRepository : ApiRepositoryCore() {

    var PER_PAGE = 200

    private inline fun <reified T> callApiWithCursor(
        url: String,
        method: ApiController.ApiMethod,
        body: Any? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ): T {
        return callApi(url, method, body, okHttpClient, buildErrorResult = { apiError, translatedErrorRes ->
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
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getFavoriteFiles(driveId: Int, order: SortType, cursor: String?): CursorApiResponse<ArrayList<File>> {
        val url = ApiRoutes.getFavoriteFiles(driveId, order) + "&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun getSharedWithMeFiles(order: SortType, cursor: String?): CursorApiResponse<List<File>> {
        return callApiWithCursor(
            url = "${ApiRoutes.getSharedWithMeFiles(order)}&${loadCursor(cursor)}",
            method = GET
        )
    }

    fun postFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), POST)

    fun deleteFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), DELETE)

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
        val url = when (cursor) {
            null -> ApiRoutes.getListingFiles(driveId, parentId, order)
            else -> "${ApiRoutes.getMoreListingFiles(driveId, parentId, order)}&${loadCursor(cursor)}"
        }
        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    // Increase timeout for this API call because it can take more than 10s to process data
    fun getFileActivities(
        file: File,
        cursor: String?,
        forFileList: Boolean,
        okHttpClient: OkHttpClient = HttpClient.okHttpClientLongTimeout,
    ): CursorApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getFileActivities(file, forFileList, loadCursor(cursor))
        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    fun getFilesLastActivities(driveId: Int, body: FileLastActivityBody): ApiResponse<List<LastFileAction>> {
        val url = "${ApiRoutes.getFilesLastActivities(driveId)}?with=file"
        return callApi(url, POST, body)
    }

    // For sync offline service
    fun getFileActivities(
        driveId: Int,
        fileIds: List<Int>,
        fromDate: Long,
        okHttpClient: OkHttpClient = HttpClient.okHttpClientLongTimeout,
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
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun startUploadSession(driveId: Int, body: StartSessionBody, okHttpClient: OkHttpClient): ApiResponse<StartUploadSession> {
        return callApi(ApiRoutes.startUploadSession(driveId), POST, body, okHttpClient = okHttpClient)
    }

    fun finishSession(driveId: Int, uploadToken: String, okHttpClient: OkHttpClient): ApiResponse<UploadSession> {
        return callApi(ApiRoutes.closeSession(driveId, uploadToken), POST, okHttpClient = okHttpClient)
    }

    fun cancelSession(driveId: Int, uploadToken: String, okHttpClient: OkHttpClient): ApiResponse<Boolean> {
        return callApi(ApiRoutes.getSession(driveId, uploadToken), DELETE, okHttpClient = okHttpClient)
    }

    fun uploadEmptyFile(uploadFile: UploadFile) = with(uploadFile) {
        val uploadUrl = ApiRoutes.uploadEmptyFileUrl(
            driveId = driveId,
            directoryId = remoteFolder,
            fileName = fileName,
            conflictOption = ConflictOption.RENAME,
            directoryPath = remoteSubFolder,
        )

        callApi<ApiResponse<File>>(uploadUrl, POST)
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
        return callApi(ApiRoutes.createOfficeFile(driveId, folderId), POST, createFile)
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
        okHttpClient: OkHttpClient = HttpClient.okHttpClient
    ): CursorApiResponse<ArrayList<File>> {
        var url = "${ApiRoutes.searchFiles(driveId, sortType)}&${loadCursor(cursor)}"
        if (!query.isNullOrBlank()) url += "&query=$query"
        if (date != null) url += "&modified_at=custom&modified_after=${date.first}&modified_before=${date.second}"
        if (type != null) url += "&type=$type"
        if (categories != null) url += "&category=$categories"

        return callApiWithCursor(url, GET, okHttpClient = okHttpClient)
    }

    fun deleteFile(file: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.fileURLV2(file), DELETE)
    }

    fun renameFile(file: File, newName: String): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.renameFile(file), POST, mapOf("name" to newName))
    }

    fun updateFolderColor(file: File, color: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateFolderColor(file), POST, mapOf("color" to color))
    }

    fun duplicateFile(file: File, destinationId: Int): ApiResponse<File> {
        return callApi(ApiRoutes.duplicateFile(file, destinationId), POST)
    }

    fun moveFile(file: File, newParent: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.moveFile(file, newParent.id), POST)
    }

    fun getFileShare(okHttpClient: OkHttpClient, file: File): ApiResponse<Share> {
        return callApi(ApiRoutes.getFileShare(file), GET, okHttpClient = okHttpClient)
    }

    fun getFileDetails(file: File, okHttpClient: OkHttpClient = HttpClient.okHttpClient): ApiResponse<File> {
        return callApi(ApiRoutes.getFileDetails(file), GET, okHttpClient = okHttpClient)
    }

    fun getFileCount(file: File): ApiResponse<FileCount> {
        return callApi(ApiRoutes.getFileCount(file), GET)
    }

    fun getFileComments(file: File, cursor: String?): CursorApiResponse<ArrayList<FileComment>> {
        val url = "${ApiRoutes.fileComments(file)}&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun postFileComment(file: File, body: String): ApiResponse<FileComment> {
        return callApi(ApiRoutes.fileComments(file), POST, mapOf("body" to body))
    }

    fun answerComment(file: File, commentId: Int, body: String): ApiResponse<FileComment> {
        return callApi(ApiRoutes.answerComment(file, commentId), POST, mapOf("body" to body))
    }

    fun putFileComment(file: File, commentId: Int, body: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileComment(file, commentId), PUT, mapOf("body" to body))
    }

    fun deleteFileComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileComment(file, commentId), DELETE)
    }

    fun postLikeComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.likeComment(file, commentId), POST)
    }

    fun postUnlikeComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.unLikeComment(file, commentId), POST)
    }

    fun getShareLink(file: File): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.shareLink(file), GET)
    }

    fun createShareLink(file: File, body: ShareLink.ShareLinkSettings): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.shareLink(file), POST, body)
    }

    fun updateShareLink(file: File, body: JsonElement): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), PUT, body)
    }

    fun deleteFileShareLink(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), DELETE)
    }

    fun getPublicShareInfo(driveId: Int, linkUuid: String): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.getPublicShareInfo(driveId, linkUuid), GET)
    }

    fun getPublicShareRootFile(driveId: Int, linkUuid: String, fileId: FileId): ApiResponse<File> {
        return callApi(ApiRoutes.getPublicShareRootFile(driveId, linkUuid, fileId), GET)
    }

    fun getPublicShareChildrenFiles(
        driveId: Int,
        linkUuid: String,
        folderId: FileId,
        sortType: SortType,
        cursor: String?,
    ): CursorApiResponse<List<File>> {
        val url = ApiRoutes.getPublicShareChildrenFiles(driveId, linkUuid, folderId, sortType) + "&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun getPublicShareFileCount(driveId: Int, linkUuid: String, fileId: Int): ApiResponse<FileCount>{
        return callApi(ApiRoutes.getPublicShareFileCount(driveId, linkUuid, fileId), GET)
    }

    fun buildPublicShareArchive(driveId: Int, linkUuid: String, archiveBody: ArchiveBody): ApiResponse<ArchiveUUID> {
        return callApi(ApiRoutes.buildPublicShareArchive(driveId, linkUuid), POST, archiveBody)
    }

    fun importPublicShareFiles(
        sourceDriveId: Int,
        linkUuid: String,
        destinationDriveId: Int,
        destinationFolderId: Int,
        fileIds: List<Int>,
        exceptedFileIds: List<Int>,
        password: String = "",
    ): ApiResponse<FileExternalImport> {
        val body: MutableMap<String, Any> = mutableMapOf(
            "source_drive_id" to sourceDriveId,
            "sharelink_uuid" to linkUuid,
            "destination_folder_id" to destinationFolderId,
        )

        if (password.isNotBlank()) body["password"] = password
        if (fileIds.isNotEmpty()) body["file_ids"] = fileIds.toTypedArray()
        if (exceptedFileIds.isNotEmpty()) body["except_file_ids"] = exceptedFileIds.toTypedArray()

        return callApi(ApiRoutes.importPublicShareFiles(destinationDriveId), POST, body)
    }

    fun postFileShareCheck(file: File, body: Map<String, Any>): ApiResponse<ArrayList<FileCheckResult>> {
        return callApi(ApiRoutes.checkFileShare(file), POST, body)
    }

    fun addMultiAccess(file: File, body: Map<String, Any?>): ApiResponse<ShareableItems> {
        return callApi(ApiRoutes.accessUrl(file), POST, body)
    }

    fun deleteFileShare(file: File, shareableItem: Shareable): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.teamAccess(file, shareableItem.id)
                is Invitation -> ApiRoutes.fileInvitationAccess(file, shareableItem.id)
                else -> ApiRoutes.userAccess(file, shareableItem.id)
            }, DELETE
        )
    }

    fun putFileShare(file: File, shareableItem: Shareable, body: Map<String, String>): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.teamAccess(file, shareableItem.id)
                is Invitation -> ApiRoutes.fileInvitationAccess(file, shareableItem.id)
                else -> ApiRoutes.userAccess(file, shareableItem.id)
            }, PUT, body
        )
    }

    fun createCategory(driveId: Int, name: String, color: String): ApiResponse<Category> {
        val body = mapOf("name" to name, "color" to color)
        return callApi(ApiRoutes.categories(driveId), POST, body)
    }

    fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): ApiResponse<Category> {
        val body = arrayMapOf("color" to color).apply {
            name?.let { put("name", it) }
        }
        return callApi(ApiRoutes.category(driveId, categoryId), PUT, body)
    }

    fun deleteCategory(driveId: Int, categoryId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.category(driveId, categoryId), DELETE)
    }

    fun addCategory(file: File, categoryId: Int): ApiResponse<ShareableItems.FeedbackAccessResource<Int, Unit>> {
        return callApi(ApiRoutes.fileCategory(file, categoryId), POST)
    }

    fun addCategory(files: List<File>, categoryId: Int): ApiResponse<List<ShareableItems.FeedbackAccessResource<Int, Unit>>> {
        val driveId = files.firstOrNull()?.driveId ?: AccountUtils.currentDriveId
        return callApi(ApiRoutes.fileCategory(driveId, categoryId), POST, mapOf("file_ids" to files.map { it.id }))
    }

    fun removeCategory(file: File, categoryId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.fileCategory(file, categoryId), DELETE)
    }

    fun removeCategory(
        files: List<File>,
        categoryId: Int,
    ): ApiResponse<List<ShareableItems.FeedbackAccessResource<Int, Unit>>> {
        val driveId = files.firstOrNull()?.driveId ?: AccountUtils.currentDriveId
        return callApi(ApiRoutes.fileCategory(driveId, categoryId), DELETE, mapOf("file_ids" to files.map { it.id }))
    }

    fun getLastActivities(driveId: Int, cursor: String?): CursorApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getLastActivities(driveId) + "&${loadCursor(cursor)}"
        return callApiWithCursor(url, GET)
    }

    fun forceFolderAccess(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.forceFolderAccess(file), POST)
    }

    fun getDropBox(file: File): ApiResponse<DropBox> {
        return callApi(ApiRoutes.dropBox(file), GET)
    }

    fun updateDropBox(file: File, body: JsonElement): ApiResponse<Boolean> {
        return callApi(ApiRoutes.dropBox(file), PUT, body)
    }

    fun postDropBox(file: File, body: Map<String, Any?>): ApiResponse<DropBox> {
        return callApi(ApiRoutes.dropBox(file), POST, body)
    }

    fun deleteDropBox(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.dropBox(file), DELETE)
    }

    fun convertFile(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.convertFile(file), POST)
    }

    fun getDriveTrash(driveId: Int, order: SortType, cursor: String?): CursorApiResponse<ArrayList<File>> {
        return callApiWithCursor("${ApiRoutes.driveTrash(driveId, order)}&${loadCursor(cursor)}", GET)
    }

    fun getTrashedFile(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.trashedFile(file), GET)
    }

    fun getTrashedFolderFiles(file: File, order: SortType, cursor: String?): CursorApiResponse<ArrayList<File>> {
        return callApiWithCursor("${ApiRoutes.trashedFolderFiles(file, order)}&${loadCursor(cursor)}", GET)
    }

    fun postRestoreTrashFile(file: File, body: Map<String, Int>?): ApiResponse<Any> =
        callApi(ApiRoutes.restoreTrashFile(file), POST, body)

    fun emptyTrash(driveId: Int): ApiResponse<Boolean> = callApi(ApiRoutes.emptyTrash(driveId), DELETE)

    fun deleteTrashFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.trashURLV2(file), DELETE)

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
        return callApi(ApiRoutes.undoAction(action.driveId), POST, mapOf("cancel_id" to action.cancelId))
    }

    fun performCancellableBulkOperation(bulkOperation: BulkOperation): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.bulkAction(bulkOperation.parent.driveId), POST, bulkOperation.toMap())
    }

    fun buildArchive(driveId: Int, archiveBody: ArchiveBody): ApiResponse<ArchiveUUID> {
        return callApi(ApiRoutes.buildArchive(driveId), POST, archiveBody)
    }

    fun cancelExternalImport(driveId: Int, importId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.cancelExternalImport(driveId, importId), PUT)
    }

    private fun pagination(page: Int, perPage: Int = PER_PAGE) = "page=$page&per_page=$perPage"

    private fun loadCursor(cursor: String?, perPage: Int = PER_PAGE): String {
        return "limit=$perPage${if (cursor == null) "" else "&cursor=$cursor"}"
    }
}
