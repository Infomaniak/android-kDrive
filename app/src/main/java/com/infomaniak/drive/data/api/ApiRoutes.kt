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

    private const val fileWithQuery = "with=capabilities,categories,conversion_capabilities,dropbox,dropbox.capabilities," +
            "external_import,is_favorite,path,sharelink,sorted_name"
    private const val fileExtraWithQuery = "$fileWithQuery,users,version"
    private const val activitiesWithQuery = "with=file,file.capabilities,file.categories,file.conversion_capabilities," +
            "file.dropbox,file.dropbox.capabilities,file.is_favorite,file.sharelink,file.sorted_name"
    const val activitiesWithExtraQuery = "$activitiesWithQuery,file.external_import"

    private fun orderQuery(order: SortType) = "order_for[${order.orderBy}]=${order.order}&order_by=${order.orderBy}"

    private fun driveURL(driveId: Int) = "${DRIVE_API_V2}${driveId}"

    private fun filesURL(driveId: Int) = "${driveURL(driveId)}/files"

    private fun fileURL(driveId: Int, fileId: FileId) = "${filesURL(driveId)}/${fileId}"

    fun fileURL(file: File) = fileURL(file.driveId, file.id)

    fun trashURL(file: File) = "${driveURL(file.driveId)}/trash/${file.id}"

    /** V1 */
    //region V1
    fun getAllDrivesData() = "${DRIVE_API_V1}init?no_avatar_default=1&with=drives,users,teams,ips,categories"
    //endregion

    /** Archive */
    //region Archive
    fun buildArchive(driveId: Int): String = "${filesURL(driveId)}/archives"

    fun downloadArchiveFiles(driveId: Int, uuid: String): String = "${buildArchive(driveId)}/$uuid"
    //endregion

    /** Access/Invitation */
    //region Access/Invitation
    fun accessUrl(file: File) = "${fileURL(file)}/access"

    fun fileInvitationAccess(file: File, invitationId: Int) = "${driveURL(file.driveId)}/files/invitations/$invitationId"

    fun getFileShare(file: File) = "${accessUrl(file)}?no_avatar_default=1&with=user"

    fun checkFileShare(file: File) = "${accessUrl(file)}/check"

    fun teamAccess(file: File, teamId: Int) = "${accessUrl(file)}/teams/$teamId"

    fun userAccess(file: File, driveUserId: Int) = "${accessUrl(file)}/users/$driveUserId"

    fun forceFolderAccess(file: File) = "${accessUrl(file)}/force"
    //endregion

    /** Action */
    //region Action
    fun undoAction(driveId: Int) = "${driveURL(driveId)}/cancel"
    //endregion

    /** Activities */
    //region Activities
    private const val activitiesActions = "actions[]=file_create" +
            "&actions[]=file_update" +
            "&actions[]=file_restore" +
            "&actions[]=file_trash" +
            "&actions[]=comment_create"

    fun getLastActivities(driveId: Int) =
        "${filesURL(driveId)}/activities?$activitiesWithQuery,user&depth=unlimited&$activitiesActions&no_avatar_default=1"

    fun getFileActivities(file: File) = "${fileURL(file)}/activities?no_avatar_default=1"

    fun getFileActivities(driveId: Int, fileIds: String, fromDate: Long) =
        "${filesURL(driveId)}/activities/batch?no_avatar_default=1&$activitiesWithQuery&file_ids=$fileIds&from_date=$fromDate" +
                "&actions[]=file_rename" +
                "&actions[]=file_update"

    fun getTrashedFilesActivities(file: File) = "${trashURL(file)}/activities"
    //endregion

    /** Category */
    //region Category

    fun categories(driveId: Int) = "${driveURL(driveId)}/categories"

    fun category(driveId: Int, categoryId: Int) = "${categories(driveId)}/$categoryId"

    fun fileCategory(file: File, categoryId: Int) = "${fileURL(file)}/categories/$categoryId"

    fun fileCategory(driveId: Int, categoryId: Int) = "${filesURL(driveId)}/categories/$categoryId"
    //endregion

    /** Comment */
    //region Comment
    private const val withComments = "no_avatar_default=1&with=user,likes,responses,responses.user,responses.likes"

    fun fileComments(file: File) = "${fileURL(file)}/comments?$withComments"

    fun fileComment(file: File, commentId: Int) = "${fileURL(file)}/comments/$commentId"

    fun answerComment(file: File, commentId: Int) = "${fileComment(file, commentId)}?$withComments"

    fun likeComment(file: File, commentId: Int) = "${fileComment(file, commentId)}/like"

    fun unLikeComment(file: File, commentId: Int) = "${fileComment(file, commentId)}/unlike"
    //endregion

    /** Dropbox */
    //region Dropbox
    fun dropBox(file: File) = "${fileURL(file)}/dropbox?with=capabilities"
    //endregion

    /** Favorite */
    //region Favorite
    fun getFavoriteFiles(driveId: Int, order: SortType) = "${filesURL(driveId)}/favorites?$fileWithQuery&${orderQuery(order)}"

    fun favorite(file: File) = "${fileURL(file)}/favorite"
    //endregion

    /** File/Directory */
    //region File/Directory
    fun getFolderFiles(driveId: Int, parentId: Int, order: SortType) =
        "${fileURL(driveId, parentId)}/files?$fileWithQuery&${orderQuery(order)}"

    fun getFileDetails(file: File) = "${fileURL(file)}?$fileExtraWithQuery"

    fun createFolder(driveId: Int, parentId: Int) = "${fileURL(driveId, parentId)}/directory?$fileWithQuery"

    fun createOfficeFile(driveId: Int, folderId: Int) = "${fileURL(driveId, folderId)}/file?$fileWithQuery"

    fun thumbnailFile(file: File) = "${fileURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun imagePreviewFile(file: File) = "${fileURL(file)}/preview?quality=80&t=${file.lastModifiedAt}"

    fun downloadFile(file: File) = "${fileURL(file)}/download"

    fun convertFile(file: File): String = "${fileURL(file)}/convert?$fileWithQuery"

    fun moveFile(file: File, newParentId: Int) = "${fileURL(file)}/move/$newParentId"

    fun duplicateFile(file: File) = "${fileURL(file)}/duplicate?$fileWithQuery"

    fun copyFile(file: File, destinationId: Int) = "${fileURL(file)}/copy/$destinationId?$fileWithQuery"

    fun renameFile(file: File) = "${fileURL(file)}/rename"

    fun getFileCount(file: File) = "${fileURL(file)}/count"

    fun getFolderSize(file: File, depth: String) = "${fileURL(file)}/size?depth=$depth"

    fun updateFolderColor(file: File) = "${fileURL(file)}/color"
    //endregion

    /** Search */
    //region Search
    fun searchFiles(driveId: Int, sortType: SortType) = "${filesURL(driveId)}/search?$fileWithQuery&${orderQuery(sortType)}"
    //endregion

    /** Share link */
    //region Share link
    fun shareLink(file: File) = "${fileURL(file)}/link"
    //endregion

    /** External import */
    //region External import
    fun cancelExternalImport(driveId: Int, importId: Int) = "${driveURL(driveId)}/imports/$importId/cancel"
    //endregion

    /** Trash */
    //region Trash
    fun driveTrash(driveId: Int, order: SortType) = "${driveURL(driveId)}/trash?${orderQuery(order)}&$fileWithQuery"

    fun emptyTrash(driveId: Int) = "${driveURL(driveId)}/trash"

    fun trashedFile(file: File) = "${trashURL(file)}?$fileExtraWithQuery"

    fun trashedFolderFiles(file: File, order: SortType) = "${trashURL(file)}/files?${orderQuery(order)}&$fileWithQuery"

    fun thumbnailTrashFile(file: File) = "${trashURL(file)}/thumbnail?t=${file.lastModifiedAt}"

    fun restoreTrashFile(file: File) = "${trashURL(file)}/restore"
    //endregion

    /** Upload */
    //region Upload
    private fun uploadSessionUrl(driveId: Int) = "${driveURL(driveId)}/upload/session"

    fun getSession(driveId: Int, uploadToken: String) = "${uploadSessionUrl(driveId)}/$uploadToken"

    fun startUploadSession(driveId: Int) = "${uploadSessionUrl(driveId)}/start"

    fun addChunkToSession(driveId: Int, uploadToken: String) = "${uploadSessionUrl(driveId)}/$uploadToken/chunk"

    fun closeSession(driveId: Int, uploadToken: String) = "${uploadSessionUrl(driveId)}/$uploadToken/finish"

    fun uploadFile(driveId: Int) = "${driveURL(driveId)}/upload"
    //endregion

    /** Root Directory */
    //region Root Directory
    fun bulkAction(driveId: Int) = "${filesURL(driveId)}/bulk"

    fun getLastModifiedFiles(driveId: Int) = "${filesURL(driveId)}/last_modified?$fileWithQuery"

    fun createTeamFolder(driveId: Int) = "${filesURL(driveId)}/team_directory?$fileWithQuery"

    fun getMySharedFiles(driveId: Int, sortType: SortType) =
        "${filesURL(driveId)}/my_shared?${orderQuery(sortType)}&$fileWithQuery,users"
    //endregion

    /** Others */
    //region Others
    fun upgradeDrive(driveId: Int) = "${SHOP_URL}drive/$driveId"

    fun orderDrive() = "${SHOP_URL}drive"

    fun renewDrive(accountId: Int) = "${MANAGER_URL}$accountId/accounts/accounting/renewal"

    fun showOffice(file: File) = "${OFFICE_URL}${file.driveId}/${file.id}"
    //endregion
}
