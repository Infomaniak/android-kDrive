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

import com.infomaniak.drive.BuildConfig.*
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.utils.FileId

object ApiRoutes {

    private const val fileWithQuery = "with=capabilities,categories,conversion,dropbox,is_favorite,sharelink,sorted_name"
    private const val fileExtraWithQuery = "$fileWithQuery,path,users,version"

    private fun orderQuery(order: SortType) = "order=${order.order}&order_by=${order.orderBy}"

    private val with = with("children")
    val withFile = with("file")

    private fun with(target: String) = "with=$target,rights,collaborative_folder,favorite,mobile,share_link,categories"

    private fun v2URL(driveId: Int) = "${DRIVE_API_V2}${driveId}"

    private fun fileURL(file: File) = "${DRIVE_API}${file.driveId}/file/${file.id}"

    private fun fileURLv2(file: File) = fileURLv2(file.driveId, file.id)

    private fun fileURLv2(driveId: Int, fileId: FileId) = "${v2URL(driveId)}/files/${fileId}"

    private fun trashURL(file: File) = "${DRIVE_API}${file.driveId}/file/trash/${file.id}"

    fun getAllDrivesData() = "${DRIVE_API}init?with=drives,users,teams,ips,categories"

    /**
     * File/Directory
     */
    fun getFolderFiles(driveId: Int, parentId: Int, order: SortType) =
        "${fileURLv2(driveId, parentId)}/files?$fileWithQuery&${orderQuery(order)}"

    fun getFileDetails(file: File) = "${fileURLv2(file)}?$fileExtraWithQuery"

    fun createFolder(driveId: Int, parentId: Int) = "${fileURLv2(driveId, parentId)}/directory?$fileWithQuery"

    fun createOfficeFile(driveId: Int, folderId: Int) = "${fileURLv2(driveId, folderId)}/file?$fileWithQuery"

    fun thumbnailFile(file: File) = "${fileURLv2(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun imagePreviewFile(file: File) = "${fileURLv2(file)}/preview?quality=80&t=${file.lastModifiedAt}"

    fun downloadFile(file: File) = "${fileURLv2(file)}/download"

    fun convertFile(file: File): String = "${fileURLv2(file)}/convert?$fileWithQuery"

    fun moveFile(file: File, newParentId: Int) = "${fileURLv2(file)}/move/$newParentId"

    fun duplicateFile(file: File) = "${fileURLv2(file)}/duplicate?$fileWithQuery"

    fun copyFile(file: File, destinationId: Int) = "${fileURLv2(file)}/copy/$destinationId?$fileWithQuery"

    fun renameFile(file: File) = "${fileURLv2(file)}/rename"

    fun getFileCount(file: File) = "${fileURLv2(file)}/count"

    fun getFolderSize(file: File, depth: String) = "${fileURLv2(file)}/size?depth=$depth"

    fun updateFolderColor(file: File) = "${fileURLv2(file)}/color"

    /**
     * File Access/Invitation
     */
    private fun accessUrl(file: File) = "${fileURLv2(file)}/access"

    fun fileInvitationAccess(file: File, invitationId: Int) = "${v2URL(file.driveId)}/files/invitations/$invitationId"

    fun getFileShare(file: File) = "${accessUrl(file)}?with=user"

    fun checkFileShare(file: File) = "${accessUrl(file)}/check"

    fun teamAccess(file: File, teamId: Int) = "${accessUrl(file)}/teams/$teamId"

    fun userAccess(file: File, driveUserId: Int) = "${accessUrl(file)}/users/$driveUserId"

    fun forceFolderAccess(file: File) = "${accessUrl(file)}/force"

    /**
     * Favorite
     */
    fun getFavoriteFiles(driveId: Int, order: SortType) = "${v2URL(driveId)}/files/favorites?$fileWithQuery&${orderQuery(order)}"

    fun favorite(file: File) = "${fileURLv2(file)}/favorite"

    /**
     * Dropbox
     */
    fun dropBox(file: File) = "${fileURLv2(file)}/dropbox"

    //

    fun createTeamFolder(driveId: Int) = "${DRIVE_API}$driveId/file/folder/team/?$with"

    fun postFileShare(file: File) = "${fileURL(file)}/share"

    fun getDriveFileTrashedListForFolder(driveId: Int, order: SortType) =
        "${DRIVE_API}${driveId}/file/trash?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun getFileTrashedListForFolder(file: File, order: SortType) =
        "${trashURL(file)}?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun deleteFile(file: File) = fileURL(file)

    fun commentFile(file: File) = "${fileURL(file)}/comment"

    fun updateComment(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId"

    fun likeCommentFile(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId/like"

    fun unLikeCommentFile(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId/unlike"

    fun uploadFile(driveId: Int, folderId: Int) = "${DRIVE_API}$driveId/file/$folderId/upload"

    fun thumbnailTrashFile(file: File) = "${trashURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun showOffice(file: File) = "${OFFICE_URL}${file.driveId}/${file.id}"

    fun getFileActivities(file: File) = "${fileURL(file)}/activity"

    fun getLastActivities(driveId: Int) =
        "${DRIVE_API}$driveId/file/activity?$withFile" +
                "&depth=unlimited" +
                "&actions[]=file_create" +
                "&actions[]=file_update" +
                "&actions[]=file_restore" +
                "&actions[]=file_trash" +
                "&actions[]=comment_create"

    fun getLastModifiedFiles(driveId: Int) = "${DRIVE_API}$driveId/file/last_modified?$with"

    fun shareLink(file: File) = "${fileURL(file)}/link"

    fun createCategory(driveId: Int) = "${DRIVE_API}$driveId/category"

    fun updateCategory(driveId: Int, categoryId: Int) = "${DRIVE_API}$driveId/category/$categoryId"

    fun addCategory(file: File) = "${fileURL(file)}/category"

    fun removeCategory(file: File, categoryId: Int) = "${fileURL(file)}/category/$categoryId"

    fun getLastPictures(driveId: Int) =
        "${DRIVE_API}$driveId/file/search?order=desc&order_by=last_modified_at&converted_type=image&$with"

    fun searchFiles(driveId: Int) = "${DRIVE_API}$driveId/file/search?$with"

    fun restoreTrashFile(file: File) = "${trashURL(file)}/restore"

    fun deleteTrashFile(file: File) = trashURL(file)

    fun emptyTrash(driveId: Int) = "${DRIVE_API}${driveId}/file/trash"

    fun getMySharedFiles(driveId: Int): String = "${DRIVE_API}$driveId/file/my_shared?$with"

    fun getUUIDArchiveFiles(driveId: Int): String = "${DRIVE_API}$driveId/file/archive"

    fun downloadArchiveFiles(driveId: Int, uuid: String): String = "${DRIVE_API}$driveId/file/archive/$uuid/download"

    fun upgradeDrive(driveId: Int): String = "${SHOP_URL}drive/$driveId"

    fun orderDrive(): String = "${SHOP_URL}drive"

    fun cancelAction(driveId: Int): String = "${DRIVE_API}$driveId/cancel"

    fun bulkAction(folder: File): String = "${DRIVE_API}${folder.driveId}/file/bulk"
}
