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
package com.infomaniak.drive.data.api

import androidx.collection.arrayMapOf
import com.google.gson.JsonElement
import com.infomaniak.drive.data.api.ApiRoutes.activitiesWithExtraQuery
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.data.models.upload.UploadSegment.ChunkStatus
import com.infomaniak.drive.data.models.upload.UploadSession
import com.infomaniak.drive.data.models.upload.UploadSession.StartSessionBody
import com.infomaniak.drive.data.models.upload.UploadSession.StartUploadSession
import com.infomaniak.drive.data.models.upload.ValidChunks
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.api.ApiController.ApiMethod.*
import com.infomaniak.lib.core.api.ApiController.callApi
import com.infomaniak.lib.core.api.ApiRepositoryCore
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import okhttp3.OkHttpClient

object ApiRepository : ApiRepositoryCore() {

    var PER_PAGE = 200

    private const val ACTIONS = "&actions[]=file_create" +
            "&actions[]=file_rename" +
            "&actions[]=file_move" +
            "&actions[]=file_move_out" +
            "&actions[]=file_trash" +
            "&actions[]=file_restore" +
            "&actions[]=file_delete" +
            "&actions[]=file_update" +
            "&actions[]=file_favorite_create" +
            "&actions[]=file_favorite_remove" +
            "&actions[]=file_share_create" +
            "&actions[]=file_share_update" +
            "&actions[]=file_share_delete" +
            "&actions[]=file_categorize" +
            "&actions[]=file_uncategorize" +
            "&actions[]=file_color_update" +
            "&actions[]=file_color_delete" +
            "&actions[]=share_link_create" +
            "&actions[]=share_link_update" +
            "&actions[]=share_link_delete" +
            "&actions[]=collaborative_folder_create" +
            "&actions[]=collaborative_folder_update" +
            "&actions[]=collaborative_folder_delete"

    private const val ADDITIONAL_ACTIONS = "&actions[]=file_access" +
            "&actions[]=comment_create" +
            "&actions[]=comment_update" +
            "&actions[]=comment_delete" +
            "&actions[]=comment_like" +
            "&actions[]=comment_unlike" +
            "&actions[]=comment_resolve" +
            "&actions[]=share_link_show"

    fun getAllDrivesData(
        okHttpClient: OkHttpClient
    ): ApiResponse<DriveInfo> {
        val url = ApiRoutes.getAllDrivesData()
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getFavoriteFiles(driveId: Int, order: File.SortType, page: Int = 1): ApiResponse<ArrayList<File>> {
        val url = ApiRoutes.getFavoriteFiles(driveId, order) + "&${pagination(page)}"
        return callApi(url, GET)
    }

    fun postFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), POST)

    fun deleteFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), DELETE)

    fun getDirectoryFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        page: Int = 1,
        order: File.SortType
    ): ApiResponse<List<File>> {
        val url = "${ApiRoutes.getFolderFiles(driveId, parentId, order)}&${pagination(page)}"
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    // Increase timeout for this api call because it can take more than 10s to process data
    fun getFileActivities(
        file: File,
        page: Int,
        forFileList: Boolean,
        okHttpClient: OkHttpClient = HttpClient.okHttpClientLongTimeout,
    ): ApiResponse<ArrayList<FileActivity>> {
        val queries = if (forFileList) {
            "&depth=children&from_date=${file.responseAt}&$activitiesWithExtraQuery"
        } else {
            "&no_avatar_default=1&with=user"
        }
        val url = "${ApiRoutes.getFileActivities(file)}?${pagination(page)}$queries$ACTIONS" +
                if (forFileList) "" else ADDITIONAL_ACTIONS

        return callApi(url, GET, okHttpClient = okHttpClient)
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

    fun getLastModifiedFiles(driveId: Int, page: Int = 1): ApiResponse<ArrayList<File>> {
        val url = "${ApiRoutes.getLastModifiedFiles(driveId)}&${pagination(page)}"
        return callApi(url, GET)
    }

    fun getLastGallery(driveId: Int, page: Int = 1): ApiResponse<ArrayList<File>> {
        val types = "&types[]=${ExtensionType.IMAGE.value}&types[]=${ExtensionType.VIDEO.value}"
        val url = "${ApiRoutes.searchFiles(driveId, File.SortType.RECENT)}$types&${pagination(page)}"
        return callApi(url, GET)
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
        sortType: File.SortType,
        page: Int,
        date: Pair<String, String>? = null,
        type: String? = null,
        categories: String? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient
    ): ApiResponse<ArrayList<File>> {
        var url = "${ApiRoutes.searchFiles(driveId, sortType)}&${pagination(page)}"
        if (!query.isNullOrBlank()) url += "&query=$query"
        if (date != null) url += "&modified_at=custom&from=${date.first}&until=${date.second}"
        if (type != null) url += "&type=$type"
        if (categories != null) url += "&category=$categories"

        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun deleteFile(file: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.fileURL(file), DELETE)
    }

    fun renameFile(file: File, newName: String): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.renameFile(file), POST, mapOf("name" to newName))
    }

    fun updateFolderColor(file: File, color: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateFolderColor(file), POST, mapOf("color" to color))
    }

    fun copyFile(file: File, copyName: String?, destinationId: Int): ApiResponse<File> {
        val body = if (copyName == null) mapOf() else mapOf("name" to copyName)
        return callApi(ApiRoutes.copyFile(file, destinationId), POST, body)
    }

    fun duplicateFile(file: File, duplicateName: String?): ApiResponse<File> {
        val body = if (duplicateName == null) mapOf() else mapOf("name" to duplicateName)
        return callApi(ApiRoutes.duplicateFile(file), POST, body)
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

    fun getFileComments(file: File, page: Int): ApiResponse<ArrayList<FileComment>> {
        val url = "${ApiRoutes.fileComments(file)}&${pagination(page)}"
        return callApi(url, GET)
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

    fun getLastActivities(driveId: Int, page: Int): ApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getLastActivities(driveId) + "&${pagination(page)}"
        return callApi(url, GET)
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

    fun getDriveTrash(driveId: Int, order: File.SortType, page: Int): ApiResponse<ArrayList<File>> {
        return callApi("${ApiRoutes.driveTrash(driveId, order)}&${pagination(page)}", GET)
    }

    fun getTrashedFile(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.trashedFile(file), GET)
    }

    fun getTrashedFolderFiles(file: File, order: File.SortType, page: Int): ApiResponse<List<File>> {
        return callApi("${ApiRoutes.trashedFolderFiles(file, order)}&${pagination(page)}", GET)
    }

    fun postRestoreTrashFile(file: File, body: Map<String, Int>?): ApiResponse<Any> =
        callApi(ApiRoutes.restoreTrashFile(file), POST, body)

    fun emptyTrash(driveId: Int): ApiResponse<Boolean> = callApi(ApiRoutes.emptyTrash(driveId), DELETE)

    fun deleteTrashFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.trashURL(file), DELETE)

    fun getMySharedFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        sortType: File.SortType,
        page: Int
    ): ApiResponse<ArrayList<File>> {
        return callApi("${ApiRoutes.getMySharedFiles(driveId, sortType)}&${pagination(page)}", GET, okHttpClient = okHttpClient)
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
}
