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
package com.infomaniak.drive.data.api

import com.infomaniak.drive.BuildConfig.*
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Team

object ApiRoutes {

    private const val with = "with=children,rights,collaborative_folder,favorite,mobile,share_link,categories"

    private fun fileURL(file: File) = "${DRIVE_API}${file.driveId}/file/${file.id}"

    private fun trashURL(file: File) = "${DRIVE_API}${file.driveId}/file/trash/${file.id}"

    fun getAllDrivesData() = "${DRIVE_API}init?with=drives,users,teams,ips,categories"

    fun getUserProfile() = "${INFOMANIAK_API}profile"

    fun checkFileShare(file: File) = "${fileURL(file)}/share/check"

    fun updateFileSharedUser(file: File, driveUser: DriveUser) = "${fileURL(file)}/share/${driveUser.id}"

    fun updateFileSharedTeam(file: File, team: Team) = "${fileURL(file)}/share/team/${team.id}"

    fun updateFileSharedInvitation(file: File, invitation: Invitation) =
        "${DRIVE_API}${file.driveId}/file/invitation/${invitation.id}"

    fun getFileShare(file: File) = "${fileURL(file)}/share?with=invitation,link,teams"

    fun postFileShare(file: File) = "${fileURL(file)}/share"

    fun createFolder(driveId: Int, parentId: Int) = "${DRIVE_API}$driveId/file/folder/$parentId?$with"

    fun createOfficeFile(driveId: Int, folderId: Int) = "${DRIVE_API}$driveId/file/file/${folderId}?$with"

    fun createTeamFolder(driveId: Int) = "${DRIVE_API}$driveId/file/folder/team/?$with"

    fun getFileListForFolder(driveId: Int, parentId: Int, order: File.SortType) =
        "${DRIVE_API}$driveId/file/$parentId?$with&order=${order.order}&order_by=${order.orderBy}"

    fun getDriveFileTrashedListForFolder(driveId: Int, order: File.SortType) =
        "${DRIVE_API}${driveId}/file/trash?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun getFileTrashedListForFolder(file: File, order: File.SortType) =
        "${trashURL(file)}?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun getFavoriteFiles(driveId: Int, order: File.SortType) =
        "${DRIVE_API}$driveId/file/favorite?$with&order=${order.order}&order_by=${order.orderBy}"

    fun favorite(file: File) =
        "${fileURL(file)}/favorite"

    fun deleteFile(file: File) = fileURL(file)

    fun downloadFile(file: File) = "${fileURL(file)}/download"

    fun commentFile(file: File) = "${fileURL(file)}/comment"

    fun updateComment(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId"

    fun likeCommentFile(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId/like"

    fun unLikeCommentFile(file: File, commentId: Int) = "${fileURL(file)}/comment/$commentId/unlike"

    fun getFileDetails(file: File) = "${fileURL(file)}?with=user,teams,children,parent,rights,favorite,version,extras," +
            "share_link,collaborative_folder,mobile,conversion,categories"

    fun getFileCount(file: File) = "${fileURL(file)}/count"

    fun moveFile(file: File, newParentId: Int) = "${fileURL(file)}/move/$newParentId"

    fun renameFile(file: File) = "${fileURL(file)}/rename"

    fun duplicateFile(file: File, folderId: Int) = "${fileURL(file)}/copy/$folderId?$with"

    fun uploadFile(driveId: Int, folderId: Int) = "${DRIVE_API}$driveId/file/$folderId/upload"

    fun thumbnailFile(file: File) = "${fileURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun thumbnailTrashFile(file: File) = "${trashURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun imagePreviewFile(file: File) = "${fileURL(file)}/preview?t=${file.lastModifiedAt}"

    fun showOffice(file: File) = "${OFFICE_URL}${file.driveId}/${file.id}"

    fun getFileActivities(file: File) = "${fileURL(file)}/activity"

    fun dropBox(file: File) = "${fileURL(file)}/collaborate"

    fun getLastActivities(driveId: Int) =
        "${DRIVE_API}$driveId/file/activity?with=file,rights,collaborative_folder,favorite,mobile,share_link&depth=unlimited" +
                "&actions[]=file_create" +
                "&actions[]=file_update" +
                "&actions[]=file_restore" +
                "&actions[]=file_trash" +
                "&actions[]=comment_create"

    fun getLastModifiedFiles(driveId: Int) = "${DRIVE_API}$driveId/file/last_modified?$with"

    fun shareLink(file: File) = "${fileURL(file)}/link"

    fun createCategory(driveId: Int) = "${DRIVE_API}$driveId/category"

    fun editCategory(driveId: Int, categoryId: Int) = "${DRIVE_API}$driveId/category/$categoryId"

    fun deleteCategory(driveId: Int, categoryId: Int) = "${DRIVE_API}$driveId/category/$categoryId"

    fun addCategory(file: File) = "${fileURL(file)}/category"

    fun removeCategory(file: File, categoryId: Int) = "${fileURL(file)}/category/$categoryId"

    fun getLastPictures(driveId: Int) =
        "${DRIVE_API}$driveId/file/search?order=desc&order_by=last_modified_at&converted_type=image&$with"

    fun searchFiles(driveId: Int) = "${DRIVE_API}$driveId/file/search?$with"

    fun postFolderAccess(file: File) = "${fileURL(file)}/share/access"

    fun restoreTrashFile(file: File) = "${trashURL(file)}/restore"

    fun deleteTrashFile(file: File) = trashURL(file)

    fun emptyTrash(driveId: Int) = "${DRIVE_API}${driveId}/file/trash"

    fun getMySharedFiles(driveId: Int): String = "${DRIVE_API}$driveId/file/my_shared?$with"

    fun getUUIDArchiveFiles(driveId: Int): String = "${DRIVE_API}$driveId/file/archive"

    fun downloadArchiveFiles(driveId: Int, uuid: String): String = "${DRIVE_API}$driveId/file/archive/$uuid/download"

    fun upgradeDrive(driveId: Int): String = "${SHOP_URL}drive/$driveId"

    fun orderDrive(): String = "${SHOP_URL}drive"

    fun convertFile(file: File): String = "${fileURL(file)}/convert"

    fun cancelAction(driveId: Int): String = "${DRIVE_API}$driveId/cancel"

    fun bulkAction(folder: File): String = "${DRIVE_API}${folder.driveId}/file/bulk"
}
