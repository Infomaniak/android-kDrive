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
    const val activitiesWithQuery =
        "with=file,file.capabilities,file.categories,file.conversion,file.dropbox,file.is_favorite,file.sharelink,file.sorted_name"

    private fun orderQuery(order: SortType) = "order_for[${order.orderBy}]=${order.order}&order_by=${order.orderBy}"

    private val with = with("children")

    private fun with(target: String) = "with=$target,rights,collaborative_folder,favorite,mobile,share_link,categories"

    private fun v2URL(driveId: Int) = "${DRIVE_API_V2}${driveId}"

    private fun fileURL(file: File) = "${DRIVE_API}${file.driveId}/file/${file.id}"

    private fun fileURLv2(driveId: Int) = "${v2URL(driveId)}/files"

    private fun fileURLv2(file: File) = fileURLv2(file.driveId, file.id)

    private fun fileURLv2(driveId: Int, fileId: FileId) = "${fileURLv2(driveId)}/${fileId}"

    private fun trashURL(file: File) = "${DRIVE_API}${file.driveId}/file/trash/${file.id}"

    private fun trashURLv2(file: File) = "${v2URL(file.driveId)}/trash/${file.id}"

    fun getAllDrivesData() = "${DRIVE_API}init?with=drives,users,teams,ips,categories"

    /** Access/Invitation */
    //region Access/Invitation
    private fun accessUrl(file: File) = "${fileURLv2(file)}/access"

    fun fileInvitationAccess(file: File, invitationId: Int) = "${v2URL(file.driveId)}/files/invitations/$invitationId"

    fun getFileShare(file: File) = "${accessUrl(file)}?with=user"

    fun checkFileShare(file: File) = "${accessUrl(file)}/check"

    fun teamAccess(file: File, teamId: Int) = "${accessUrl(file)}/teams/$teamId"

    fun userAccess(file: File, driveUserId: Int) = "${accessUrl(file)}/users/$driveUserId"

    fun forceFolderAccess(file: File) = "${accessUrl(file)}/force"
    //endregion

    /** Activities **/
    //region Activities
    private const val activitiesActions = "actions[]=file_create" +
            "&actions[]=file_update" +
            "&actions[]=file_restore" +
            "&actions[]=file_trash" +
            "&actions[]=comment_create"

    fun getLastActivities(driveId: Int) =
        "${fileURLv2(driveId)}/activities?$activitiesWithQuery,user&depth=unlimited&$activitiesActions"

    fun getFileActivities(file: File) = "${fileURLv2(file)}/activities"

    fun getFileActivities(driveId: Int, fileIds: String, fromDate: Long) =
        "${fileURLv2(driveId)}/activities/batch?$activitiesWithQuery&file_ids=$fileIds&from_date=$fromDate" +
                "&actions[]=file_rename" +
                "&actions[]=file_update"

    fun getTrashedFilesActivities(file: File) = "${trashURLv2(file)}/activities"
    //endregion

    /** Category **/
    //region Category

    fun categories(driveId: Int) = "${v2URL(driveId)}/categories"

    fun category(driveId: Int, categoryId: Int) = "${categories(driveId)}/$categoryId"

    fun fileCategory(file: File, categoryId: Int) = "${fileURLv2(file)}/categories/$categoryId"

    fun fileCategory(driveId: Int, categoryId: Int) = "${fileURLv2(driveId)}/categories/$categoryId"
    //endregion

    /** Comment **/
    //region Comment
    private const val withComments = "with=user,likes,responses,responses.user,responses.likes"

    fun fileComments(file: File) = "${fileURLv2(file)}/comments?$withComments"

    fun fileComment(file: File, commentId: Int) = "${fileURLv2(file)}/comments/$commentId"

    fun answerComment(file: File, commentId: Int) = "${fileComment(file, commentId)}?$withComments"

    fun likeComment(file: File, commentId: Int) = "${fileComment(file, commentId)}/like"

    fun unLikeComment(file: File, commentId: Int) = "${fileComment(file, commentId)}/unlike"
    //endregion

    /** Dropbox **/
    //region Dropbox
    fun dropBox(file: File) = "${fileURLv2(file)}/dropbox"
    //endregion

    /** Favorite **/
    //region Favorite
    fun getFavoriteFiles(driveId: Int, order: SortType) = "${fileURLv2(driveId)}/favorites?$fileWithQuery&${orderQuery(order)}"

    fun favorite(file: File) = "${fileURLv2(file)}/favorite"
    //endregion

    /** File/Directory **/
    //region File/Directory
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
    //endregion

    /** Root Directory **/
    //region Root Directory
    fun bulkAction(driveId: Int): String = "${fileURLv2(driveId)}/bulk"

    fun getLastModifiedFiles(driveId: Int) = "${fileURLv2(driveId)}/last_modified?$fileWithQuery"

    fun createTeamFolder(driveId: Int) = "${fileURLv2(driveId)}/team_directory?$fileWithQuery"

    fun getMySharedFiles(driveId: Int, sortType: SortType) =
        "${fileURLv2(driveId)}/my_shared?${orderQuery(sortType)}&$fileWithQuery,users"
    //endregion

    fun postFileShare(file: File) = "${fileURL(file)}/share"

    fun getDriveFileTrashedListForFolder(driveId: Int, order: SortType) =
        "${DRIVE_API}${driveId}/file/trash?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun getFileTrashedListForFolder(file: File, order: SortType) =
        "${trashURL(file)}?with=children,parent,extras&order=${order.order}&order_by=${order.orderBy}"

    fun deleteFile(file: File) = fileURL(file)

    fun uploadFile(driveId: Int, folderId: Int) = "${DRIVE_API}$driveId/file/$folderId/upload"

    fun thumbnailTrashFile(file: File) = "${trashURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun showOffice(file: File) = "${OFFICE_URL}${file.driveId}/${file.id}"

    fun shareLink(file: File) = "${fileURL(file)}/link"

    fun getLastPictures(driveId: Int) =
        "${DRIVE_API}$driveId/file/search?order=desc&order_by=last_modified_at&converted_type=image&$with"

    fun searchFiles(driveId: Int) = "${DRIVE_API}$driveId/file/search?$with"

    fun restoreTrashFile(file: File) = "${trashURL(file)}/restore"

    fun deleteTrashFile(file: File) = trashURL(file)

    fun emptyTrash(driveId: Int) = "${DRIVE_API}${driveId}/file/trash"

    fun getUUIDArchiveFiles(driveId: Int): String = "${DRIVE_API}$driveId/file/archive"

    fun downloadArchiveFiles(driveId: Int, uuid: String): String = "${DRIVE_API}$driveId/file/archive/$uuid/download"

    fun upgradeDrive(driveId: Int): String = "${SHOP_URL}drive/$driveId"

    fun orderDrive(): String = "${SHOP_URL}drive"

    fun cancelAction(driveId: Int): String = "${DRIVE_API}$driveId/cancel"
}
