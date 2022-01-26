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
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.ApiController.ApiMethod.*
import com.infomaniak.lib.core.utils.ApiController.callApi
import okhttp3.OkHttpClient

object ApiRepository {

    var PER_PAGE = 200
    private const val ACTIONS = "&actions[]=file_move" +
            "&actions[]=file_trash" +
            "&actions[]=file_create" +
            "&actions[]=file_update" +
            "&actions[]=file_rename" +
            "&actions[]=file_delete" +
            "&actions[]=file_restore" +
            "&actions[]=file_move_out" +
            "&actions[]=file_share_create" +
            "&actions[]=file_share_update" +
            "&actions[]=file_share_delete" +
            "&actions[]=file_favorite_create" +
            "&actions[]=file_favorite_remove" +
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

    fun getAllDrivesData(
        okHttpClient: OkHttpClient
    ): ApiResponse<DriveInfo> {
        val url = ApiRoutes.getAllDrivesData()
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getUserProfile(
        okHttpClient: OkHttpClient
    ): ApiResponse<User> {
        val url = "${ApiRoutes.getUserProfile()}?with=avatar,phones,emails"
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getFavoriteFiles(driveId: Int, order: File.SortType, page: Int = 1): ApiResponse<ArrayList<File>> {
        val url = ApiRoutes.getFavoriteFiles(driveId, order) + "&${pagination(page)}"
        return callApi(url, GET)
    }

    fun postFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), POST)

    fun deleteFavoriteFile(file: File): ApiResponse<Boolean> = callApi(ApiRoutes.favorite(file), DELETE)

    fun getFileListForFolder(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        page: Int = 1,
        order: File.SortType
    ): ApiResponse<File> {
        val url = "${ApiRoutes.getFileListForFolder(driveId, parentId, order)}&${pagination(page)}"
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getFileActivities(okHttpClient: OkHttpClient, file: File, page: Int): ApiResponse<ArrayList<FileActivity>> {
        val url = "${ApiRoutes.getFileActivities(file)}?${pagination(page)}&depth=children&from_date=${file.responseAt}" +
                "&with=file,rights,collaborative_folder,favorite,share_link,mobile,categories" + ACTIONS
        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun getLastModifiedFiles(driveId: Int, page: Int = 1): ApiResponse<ArrayList<File>> {
        val url = "${ApiRoutes.getLastModifiedFiles(driveId)}&${pagination(page)}"
        return callApi(url, GET)
    }

    fun getLastPictures(driveId: Int, page: Int = 1): ApiResponse<ArrayList<File>> {
        return callApi("${ApiRoutes.getLastPictures(driveId)}&${pagination(page)}", GET)
    }

    fun getValidChunks(driveId: Int, folderId: Int, uploadIdentifier: String): ApiResponse<ValidChunks> {
        val url = "${ApiRoutes.uploadFile(driveId, folderId)}/$uploadIdentifier?with=valid_chunks"
        return callApi(url, GET)
    }

    fun createFolder(
        okHttpClient: OkHttpClient,
        driveId: Int,
        parentId: Int,
        name: String,
        onlyForMe: Boolean = false,
        share: Boolean = false
    ): ApiResponse<File> {
        return callApi(
            ApiRoutes.createFolder(driveId, parentId),
            POST,
            mapOf("name" to name, "only_for_me" to onlyForMe, "share" to share),
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
        return callApi(
            ApiRoutes.createTeamFolder(driveId),
            POST,
            mapOf("name" to name, "for_all_users" to forAllUsers),
            okHttpClient = okHttpClient
        )
    }

    fun searchFiles(
        driveId: Int,
        query: String? = null,
        order: String,
        orderBy: String,
        page: Int,
        date: Pair<String, String>? = null,
        type: String? = null,
        categories: String? = null,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient
    ): ApiResponse<ArrayList<File>> {
        var url = "${ApiRoutes.searchFiles(driveId)}&order=$order&order_by=$orderBy&${pagination(page)}"
        if (query != null) url += "&query=$query"
        if (date != null) url += "&modified_at=custom&from=${date.first}&until=${date.second}"
        if (type != null) url += "&converted_type=$type"
        if (categories != null) url += "&category=$categories"

        return callApi(url, GET, okHttpClient = okHttpClient)
    }

    fun deleteFile(file: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.deleteFile(file), DELETE)
    }

    fun renameFile(file: File, newName: String): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.renameFile(file), POST, mapOf("name" to newName))
    }

    fun updateFolderColor(file: File, color: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateFolderColor(file), POST, mapOf("color" to color))
    }

    fun duplicateFile(file: File, copyName: String?, folderId: Int): ApiResponse<File> {
        val body = if (copyName == null) mapOf() else mapOf("name" to copyName)
        return callApi(ApiRoutes.duplicateFile(file, folderId), POST, body)
    }

    fun moveFile(file: File, newParent: File): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.moveFile(file, newParent.id), POST)
    }

    fun getFileShare(okHttpClient: OkHttpClient, file: File): ApiResponse<Share> {
        return callApi(ApiRoutes.getFileShare(file), GET, okHttpClient = okHttpClient)
    }

    fun getFileDetails(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.getFileDetails(file), GET)
    }

    fun getFileCount(file: File): ApiResponse<FileCount> {
        return callApi(ApiRoutes.getFileCount(file), GET)
    }

    fun getFileActivities(file: File, page: Int): ApiResponse<ArrayList<FileActivity>> {
        val url = "${ApiRoutes.getFileActivities(file)}?with=user&${pagination(page, 25)}" + ACTIONS
        return callApi(url, GET)
    }

    fun getFileComments(file: File, page: Int): ApiResponse<ArrayList<FileComment>> {
        val url = "${ApiRoutes.commentFile(file)}?with=like,response&${pagination(page)}"
        return callApi(url, GET)
    }

    fun postFileComment(file: File, body: String): ApiResponse<FileComment> {
        return callApi(ApiRoutes.commentFile(file), POST, mapOf("body" to body))
    }

    fun putFileComment(file: File, commentId: Int, body: String): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateComment(file, commentId), PUT, mapOf("body" to body))
    }

    fun deleteFileComment(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateComment(file, commentId), DELETE)
    }

    fun postFileCommentLike(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.likeCommentFile(file, commentId), POST)
    }

    fun postFileCommentUnlike(file: File, commentId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.unLikeCommentFile(file, commentId), POST)
    }

    fun postFileShareLink(file: File, body: Map<String, String>): ApiResponse<ShareLink> {
        return callApi(ApiRoutes.shareLink(file), POST, body)
    }

    fun deleteFileShareLink(file: File): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), DELETE)
    }

    fun postFileShareCheck(file: File, body: Map<String, Any>): ApiResponse<ArrayList<FileCheckResult>> {
        return callApi(ApiRoutes.checkFileShare(file), POST, body)
    }

    fun postFileShare(file: File, body: Map<String, Any?>): ApiResponse<ShareResults> {
        return callApi(ApiRoutes.postFileShare(file), POST, body)
    }

    fun deleteFileShare(file: File, shareableItem: Shareable): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.updateFileSharedTeam(file, shareableItem)
                is Invitation -> ApiRoutes.updateFileSharedInvitation(file, shareableItem)
                else -> ApiRoutes.updateFileSharedUser(file, shareableItem as DriveUser)
            }, DELETE
        )
    }

    fun putFileShare(file: File, shareableItem: Shareable, body: Map<String, String>): ApiResponse<Boolean> {
        return callApi(
            when (shareableItem) {
                is Team -> ApiRoutes.updateFileSharedTeam(file, shareableItem)
                is Invitation -> ApiRoutes.updateFileSharedInvitation(file, shareableItem)
                else -> ApiRoutes.updateFileSharedUser(file, shareableItem as DriveUser)
            }, PUT, body
        )
    }

    fun putFileShareLink(file: File, body: Map<String, Any?>): ApiResponse<Boolean> {
        return callApi(ApiRoutes.shareLink(file), PUT, body)
    }

    fun createCategory(driveId: Int, name: String, color: String): ApiResponse<Category> {
        val body = mapOf("name" to name, "color" to color)
        return callApi(ApiRoutes.createCategory(driveId), POST, body)
    }

    fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): ApiResponse<Category> {
        val body = arrayMapOf("color" to color).apply {
            name?.let { put("name", it) }
        }
        return callApi(ApiRoutes.updateCategory(driveId, categoryId), PATCH, body)
    }

    fun deleteCategory(driveId: Int, categoryId: Int): ApiResponse<Boolean> {
        return callApi(ApiRoutes.updateCategory(driveId, categoryId), DELETE)
    }

    fun addCategory(file: File, categoryId: Int): ApiResponse<Unit> {
        val body = mapOf("id" to categoryId)
        return callApi(ApiRoutes.addCategory(file), POST, body)
    }

    fun removeCategory(file: File, categoryId: Int): ApiResponse<Unit> {
        return callApi(ApiRoutes.removeCategory(file, categoryId), DELETE)
    }

    fun getLastActivities(driveId: Int, page: Int): ApiResponse<ArrayList<FileActivity>> {
        val url = ApiRoutes.getLastActivities(driveId) + "&${pagination(page)}"
        return callApi(url, GET)
    }

    fun postFolderAccess(file: File): ApiResponse<File> {
        return callApi(ApiRoutes.postFolderAccess(file), POST)
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
        return callApi("${ApiRoutes.getDriveFileTrashedListForFolder(driveId, order)}&${pagination(page)}", GET)
    }

    fun getTrashFile(file: File, order: File.SortType, page: Int): ApiResponse<File> {
        return callApi("${ApiRoutes.getFileTrashedListForFolder(file, order)}&${pagination(page)}", GET)
    }

    fun postRestoreTrashFile(file: File, body: Map<String, Int>?): ApiResponse<Any> =
        callApi(ApiRoutes.restoreTrashFile(file), POST, body)

    fun emptyTrash(driveId: Int): ApiResponse<Boolean> = callApi(ApiRoutes.emptyTrash(driveId), DELETE)

    fun deleteTrashFile(file: File): ApiResponse<Any> = callApi(ApiRoutes.deleteTrashFile(file), DELETE)

    fun getMySharedFiles(
        okHttpClient: OkHttpClient,
        driveId: Int,
        order: String,
        orderBy: String,
        page: Int
    ): ApiResponse<ArrayList<File>> {
        return callApi(
            "${ApiRoutes.getMySharedFiles(driveId)}&order=$order&order_by=$orderBy&${pagination(page)}",
            GET,
            okHttpClient = okHttpClient
        )
    }

    fun cancelAction(action: CancellableAction): ApiResponse<Boolean> {
        return callApi(ApiRoutes.cancelAction(action.driveId), POST, mapOf("cancel_id" to action.cancelId))
    }

    fun performCancellableBulkOperation(bulkOperation: BulkOperation): ApiResponse<CancellableAction> {
        return callApi(ApiRoutes.bulkAction(bulkOperation.parent), POST, bulkOperation.toMap())
    }

    fun getUUIDArchiveFiles(driveId: Int, fileIds: IntArray): ApiResponse<ArchiveUUID> {
        return callApi(ApiRoutes.getUUIDArchiveFiles(driveId), POST, mapOf("file_ids" to fileIds))
    }

    private fun pagination(page: Int, perPage: Int = PER_PAGE) = "page=$page&per_page=$perPage"
}
