/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.drive.data.cache

import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController.saveRemoteFiles
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.networking.HttpClient
import io.realm.Realm
import io.realm.RealmQuery
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.Calendar

object FolderFilesProvider {

    // Bump this when we want to force-refresh files that are too old.
    // Example: We did it when we added Categories & Colored folders, to automatically display them when updating the app.
    private const val MIN_VERSION_CODE = 4_02_000_08

    private val minDateToIgnoreCache = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.timeInMillis / 1000 // 3 month

    private val ROOT_FILE get() = File(Utils.ROOT_ID, name = "Root", driveId = AccountUtils.currentDriveId)

    fun getFilesFromCacheOrDownload(
        parentId: Int,
        isFirstPage: Boolean,
        ignoreCache: Boolean = false,
        ignoreCloud: Boolean = false,
        order: File.SortType = File.SortType.NAME_AZ,
        userDrive: UserDrive?,
        customRealm: Realm? = null,
        withChildren: Boolean = true,
    ): Pair<File, ArrayList<File>>? {

        fun hasDuplicatesFiles(query: RealmQuery<File>): Boolean {
            return query.count() != query.distinct(File::id.name).count()
        }

        val operation: (Realm) -> Pair<File, ArrayList<File>>? = operation@{ realm ->
            var result: Pair<File, ArrayList<File>>? = null
            var folderProxy = FileController.getFileById(realm, parentId)
            val localFolderWithoutChildren = folderProxy?.let { realm.copyFromRealm(it, 1) }
            val hasDuplicatesFiles = folderProxy?.children?.where()?.let(::hasDuplicatesFiles) ?: false
            if (!isFirstPage && folderProxy?.cursor == null) return@operation null

            val needToDownload = ignoreCache
                    || folderProxy == null
                    || folderProxy.children.isEmpty()
                    || !folderProxy.isComplete
                    || folderProxy.versionCode < MIN_VERSION_CODE
                    || hasDuplicatesFiles
                    || minDateToIgnoreCache >= folderProxy.responseAt

            if (needToDownload && !ignoreCloud) {
                val (okHttpClient, driveId) = if (userDrive == null) {
                    HttpClient.okHttpClient to AccountUtils.currentDriveId
                } else {
                    runBlocking { AccountUtils.getHttpClient(userDrive.userId) } to userDrive.driveId
                }

                if (parentId == Utils.ROOT_ID) {
                    refreshRootFolder(realm, driveId, okHttpClient)
                    folderProxy = FileController.getFileById(realm, parentId)
                }
                result = realm.downloadAndSaveFiles(
                    localFolderProxy = folderProxy,
                    order = order,
                    isFirstPage = isFirstPage,
                    parentId = parentId,
                    driveId = driveId,
                    okHttpClient = okHttpClient,
                    withChildren = withChildren
                )
            } else if (isFirstPage && localFolderWithoutChildren != null) {
                val localSortedFolderFiles =
                    if (withChildren) FileController.getLocalSortedFolderFiles(folderProxy, order) else arrayListOf()
                result = (localFolderWithoutChildren to localSortedFolderFiles)
            }
            result
        }
        return customRealm?.let(operation) ?: FileController.getRealmInstance(userDrive).use(operation)
    }

    fun getCloudStorageFiles(
        parentId: Int,
        userDrive: UserDrive,
        sortType: File.SortType,
        isFirstPage: Boolean = true,
        transaction: (files: ArrayList<File>) -> Unit
    ) {
        val filesFromCacheOrDownload = getFilesFromCacheOrDownload(
            parentId = parentId,
            isFirstPage = isFirstPage,
            ignoreCache = true,
            order = sortType,
            userDrive = userDrive
        )
        val files = filesFromCacheOrDownload?.second ?: arrayListOf()
        transaction(files)
        if (files.size >= ApiRepository.PER_PAGE) getCloudStorageFiles(parentId, userDrive, sortType, false, transaction)
    }

    fun getFolderActivities(folder: File, userDrive: UserDrive? = null): Map<out Int, FileActivity> {
        return FileController.getRealmInstance(userDrive).use { realm ->
            getFolderActivitiesRec(realm, folder, userDrive)
        }
    }

    private fun refreshRootFolder(realm: Realm, driveId: Int, okHttpClient: OkHttpClient) {
        val localRoot = FileController.getFileById(realm, Utils.ROOT_ID)
        val remoteRootFolder =
            ApiRepository.getFileDetails(File(id = Utils.ROOT_ID, driveId = driveId), okHttpClient).data?.apply {
                localRoot?.let { FileController.keepOldLocalFilesData(it, this) }
            }
        val rootFolder = remoteRootFolder ?: localRoot ?: ROOT_FILE
        realm.executeTransaction { realm.copyToRealmOrUpdate(rootFolder) }
    }

    private fun Realm.downloadAndSaveFiles(
        localFolderProxy: File?,
        order: File.SortType,
        isFirstPage: Boolean,
        parentId: Int,
        driveId: Int,
        okHttpClient: OkHttpClient,
        withChildren: Boolean,
    ): Pair<File, ArrayList<File>>? {
        var result: Pair<File, ArrayList<File>>? = null

        val apiResponse = ApiRepository.getFolderFiles(okHttpClient, driveId, parentId, localFolderProxy?.cursor, order)
        val localFolder = localFolderProxy?.realm?.copyFromRealm(localFolderProxy, 1)
            ?: ApiRepository.getFileDetails(File(id = parentId, driveId = driveId)).data

        if (apiResponse.isSuccess()) {
            val remoteFiles = apiResponse.data
            if (remoteFiles != null && localFolder != null) {
                saveRemoteFiles(
                    realm = this,
                    localFolderProxy = localFolderProxy,
                    remoteFolder = localFolder,
                    apiResponse = apiResponse,
                    isFirstPage = isFirstPage,
                    isCompleteFolder = remoteFiles.count() < ApiRepository.PER_PAGE,
                )
                result = (localFolder to if (withChildren) ArrayList(remoteFiles) else arrayListOf())
            }
        } else if (isFirstPage && localFolderProxy != null) {
            val localSortedFolderFiles =
                if (withChildren) FileController.getLocalSortedFolderFiles(localFolderProxy, order) else arrayListOf()
            result = (localFolder!! to localSortedFolderFiles)
        }
        return result
    }

    private tailrec fun getFolderActivitiesRec(
        realm: Realm,
        folder: File,
        userDrive: UserDrive? = null,
        cursor: String? = null,
        returnResponse: ArrayMap<Int, FileActivity> = arrayMapOf(),
    ): Map<out Int, FileActivity> {
        val okHttpClient = runBlocking {
            userDrive?.userId?.let { AccountUtils.getHttpClient(it, 30) } ?: HttpClient.okHttpClientLongTimeout
        }
        val apiResponse = ApiRepository.getFileActivities(folder, cursor, true, okHttpClient)
        if (!apiResponse.isSuccess()) return returnResponse

        return if (apiResponse.data?.isNotEmpty() == true) {
            apiResponse.data?.forEach { fileActivity ->
                fileActivity.applyFileActivity(realm, returnResponse, folder)
            }

            if (apiResponse.hasMore && apiResponse.cursor != null) {
                // Loading the next page, then the cursor is required
                getFolderActivitiesRec(realm, folder, userDrive, apiResponse.cursor, returnResponse)
            } else {
                if (apiResponse.responseAt > 0L) {
                    FileController.updateFile(folder.id, realm) { file ->
                        file.responseAt = apiResponse.responseAt
                        apiResponse.cursor?.let { file.cursor = it }
                    }
                } else {
                    Sentry.withScope { scope ->
                        scope.setExtra("data", apiResponse.toString())
                        Sentry.captureMessage("response at is null")
                    }
                }
                returnResponse

            }
        } else {
            if (apiResponse.responseAt > 0L) {
                FileController.updateFile(folder.id, realm) { file -> file.responseAt = apiResponse.responseAt }
            } else {
                Sentry.withScope { scope ->
                    scope.setExtra("data", apiResponse.toString())
                    Sentry.captureMessage("response at is null")
                }
            }
            returnResponse
        }
    }

    private fun FileActivity.applyFileActivity(realm: Realm, returnResponse: ArrayMap<Int, FileActivity>, currentFolder: File) {
        val remoteFile = file
        when (val action = getAction()) {
            FileActivity.FileActivityType.FILE_DELETE,
            FileActivity.FileActivityType.FILE_MOVE_OUT,
            FileActivity.FileActivityType.FILE_TRASH -> {
                if (returnResponse[fileId] == null || returnResponse[fileId]?.createdAt?.time == createdAt.time) { // Api fix
                    FileController.getParentFile(fileId = fileId, realm = realm)?.let { localFolder ->
                        if (localFolder.id != currentFolder.id) return@let

                        if (action == FileActivity.FileActivityType.FILE_MOVE_OUT) {
                            FileController.updateFile(localFolder.id, realm) { it.children.remove(file) }
                        } else {
                            FileController.removeFile(fileId, customRealm = realm, recursive = false)
                        }
                    }

                    returnResponse[fileId] = this
                }
            }
            FileActivity.FileActivityType.FILE_CREATE,
            FileActivity.FileActivityType.FILE_MOVE_IN,
            FileActivity.FileActivityType.FILE_RESTORE -> {
                if (returnResponse[fileId] == null && remoteFile != null) {
                    if (remoteFile.isImporting()) MqttClientWrapper.start(remoteFile.externalImport?.id)
                    realm.where(File::class.java).equalTo(File::id.name, currentFolder.id).findFirst()?.let { realmFolder ->
                        if (!realmFolder.children.contains(remoteFile)) {
                            realm.executeTransaction { realmFolder.children.add(remoteFile) }
                        } else {
                            FileController.updateFileFromActivity(realm, remoteFile, realmFolder.id)
                        }
                        returnResponse[fileId] = this
                    }
                }
            }
            FileActivity.FileActivityType.COLLABORATIVE_FOLDER_CREATE,
            FileActivity.FileActivityType.COLLABORATIVE_FOLDER_DELETE,
            FileActivity.FileActivityType.COLLABORATIVE_FOLDER_UPDATE,
            FileActivity.FileActivityType.FILE_FAVORITE_CREATE,
            FileActivity.FileActivityType.FILE_FAVORITE_REMOVE,
            FileActivity.FileActivityType.FILE_RENAME,
            FileActivity.FileActivityType.FILE_CATEGORIZE,
            FileActivity.FileActivityType.FILE_UNCATEGORIZE,
            FileActivity.FileActivityType.FILE_COLOR_UPDATE,
            FileActivity.FileActivityType.FILE_COLOR_DELETE,
            FileActivity.FileActivityType.FILE_SHARE_CREATE,
            FileActivity.FileActivityType.FILE_SHARE_DELETE,
            FileActivity.FileActivityType.FILE_SHARE_UPDATE,
            FileActivity.FileActivityType.FILE_UPDATE -> {
                if (returnResponse[fileId] == null) {
                    if (remoteFile == null) {
                        FileController.removeFile(fileId, customRealm = realm, recursive = false)
                    } else {
                        FileController.updateFileFromActivity(realm, remoteFile, currentFolder.id)
                    }
                    returnResponse[fileId] = this
                }
            }
            else -> Unit
        }
    }

}
