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
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FileController.saveRemoteFiles
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType.ONLY_FROM_REMOTE
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileAction
import com.infomaniak.drive.data.models.FileActivityType
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FileId
import com.infomaniak.drive.utils.Utils.ROOT_ID
import io.realm.Realm
import io.realm.RealmQuery
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.Calendar

object FolderFilesProvider {

    // Bump this when we want to force-refresh files that are too old.
    // Example: We did it when we added Categories & Colored folders, to automatically display them when updating the app.
    private const val MIN_VERSION_CODE = 4_02_000_08

    private val minDateToIgnoreCache = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.timeInMillis / 1000 // 3 month

    fun getFiles(folderFilesProviderArgs: FolderFilesProviderArgs): FolderFilesProviderResult? {
        val realm = folderFilesProviderArgs.realm ?: FileController.getRealmInstance(folderFilesProviderArgs.userDrive)

        val folderProxy = FileController.getFileById(realm, folderFilesProviderArgs.folderId)
        val sourceRestrictionType = folderFilesProviderArgs.sourceRestrictionType
        val needToLoadFromRemote = needToLoadFromRemote(sourceRestrictionType, folderProxy)

        val files = when {
            needToLoadFromRemote && sourceRestrictionType != SourceRestrictionType.ONLY_FROM_LOCAL -> {
                loadFromRemote(realm, folderProxy, folderFilesProviderArgs, isCloudStorage = false)
            }
            folderFilesProviderArgs.isFirstPage -> {
                loadFromLocal(realm, folderProxy, folderFilesProviderArgs.withChildren, folderFilesProviderArgs.order)
            }
            else -> {
                null
            }
        }

        if (folderFilesProviderArgs.realm == null) realm.close()

        return files
    }

    fun getCloudStorageFiles(folderFilesProviderArgs: FolderFilesProviderArgs): FolderFilesProviderResult? {
        val realm = folderFilesProviderArgs.realm ?: FileController.getRealmInstance(folderFilesProviderArgs.userDrive)
        val folderProxy = FileController.getFileById(realm, folderFilesProviderArgs.folderId)
        val files = loadFromRemote(realm, folderProxy, folderFilesProviderArgs, isCloudStorage = true)
        if (folderFilesProviderArgs.realm == null) realm.close()
        return files
    }

    fun tryLoadActivitiesFromFolder(folder: File, userDrive: UserDrive, activitiesJob: Job): Boolean {
        val realm = FileController.getRealmInstance(userDrive)
        val folderProxy = FileController.getFileById(realm, folder.id) ?: return false
        if (!folderProxy.isComplete) return false

        val okHttpClient = runBlocking { AccountUtils.getHttpClient(userDrive.userId, 30) }
        val result = loadActivitiesFromFolderRec(activitiesJob, folderProxy, userDrive, okHttpClient)

        realm.close()

        return result.isNotEmpty()
    }

    private fun loadFromRemote(
        realm: Realm,
        folderProxy: File?,
        folderFilesProviderArgs: FolderFilesProviderArgs,
        isCloudStorage: Boolean,
    ): FolderFilesProviderResult? = with(Dispatchers.IO) {

        val userDrive = folderFilesProviderArgs.userDrive
        val (okHttpClient, driveId) = runBlocking { AccountUtils.getHttpClient(userDrive.userId) } to userDrive.driveId

        val apiResponse = if (folderFilesProviderArgs.folderId == ROOT_ID || isCloudStorage) {
            ApiRepository.getFolderFiles(
                okHttpClient = okHttpClient,
                driveId = driveId,
                parentId = folderFilesProviderArgs.folderId,
                cursor = folderProxy?.cursor,
                order = folderFilesProviderArgs.order
            )

        } else {
            ApiRepository.getListingFiles(
                okHttpClient = okHttpClient,
                driveId = driveId,
                parentId = folderFilesProviderArgs.folderId,
                cursor = if (folderFilesProviderArgs.sourceRestrictionType == ONLY_FROM_REMOTE) null else folderProxy?.cursor,
                order = folderFilesProviderArgs.order
            ).let {
                CursorApiResponse(
                    result = it.result,
                    data = it.data?.files,
                    error = it.error,
                    responseAt = it.responseAt,
                    cursor = it.cursor,
                    hasMore = it.hasMore,
                )
            }
        }

        ensureActive()

        val localFolder = folderProxy?.let { realm.copyFromRealm(it, 1) }
            ?: ApiRepository.getFileDetails(File(id = folderFilesProviderArgs.folderId, driveId = driveId)).data
            ?: return@with null

        handleRemoteFiles(realm, apiResponse, folderFilesProviderArgs, folderProxy, localFolder)
    }

    private fun handleRemoteFiles(
        realm: Realm,
        apiResponse: CursorApiResponse<List<File>>,
        folderFilesProviderArgs: FolderFilesProviderArgs,
        folderProxy: File?,
        localFolder: File,
    ): FolderFilesProviderResult? {
        val apiResponseData = apiResponse.data
        val folderWithChildren = folderFilesProviderArgs.withChildren

        return when {
            !apiResponseData.isNullOrEmpty() -> {
                val isCompleteFolder = !apiResponse.hasMore || apiResponseData.count() < ApiRepository.PER_PAGE
                saveRemoteFiles(
                    realm = realm,
                    localFolderProxy = folderProxy,
                    remoteFolder = localFolder,
                    apiResponse = apiResponse,
                    isFirstPage = folderFilesProviderArgs.isFirstPage,
                    isCompleteFolder = isCompleteFolder
                )
                val folderFiles = if (folderWithChildren) ArrayList(apiResponseData) else arrayListOf()
                FolderFilesProviderResult(folder = localFolder, folderFiles = folderFiles, isComplete = isCompleteFolder)
            }
            folderFilesProviderArgs.isFirstPage -> {
                loadFromLocal(realm, folderProxy, folderWithChildren, folderFilesProviderArgs.order)
            }
            else -> {
                null
            }
        }
    }

    private fun loadFromLocal(
        realm: Realm,
        folderProxy: File?,
        withChildren: Boolean,
        order: File.SortType
    ): FolderFilesProviderResult? {
        val localFolderWithoutChildren = folderProxy?.let { realm.copyFromRealm(it, 1) } ?: return null
        val sortedFolderFiles = if (withChildren) FileController.getLocalSortedFolderFiles(folderProxy, order) else arrayListOf()
        return FolderFilesProviderResult(folder = localFolderWithoutChildren, folderFiles = sortedFolderFiles, isComplete = true)
    }

    private fun needToLoadFromRemote(sourceRestrictionType: SourceRestrictionType, folderProxy: File?): Boolean {

        fun hasDuplicatesFiles(query: RealmQuery<File>): Boolean = query.count() != query.distinct(File::id.name).count()

        return sourceRestrictionType == ONLY_FROM_REMOTE
                || folderProxy == null
                || folderProxy.children.isEmpty()
                || !folderProxy.isComplete
                || folderProxy.versionCode < MIN_VERSION_CODE
                || folderProxy.children.where().let(::hasDuplicatesFiles)
                || minDateToIgnoreCache >= folderProxy.responseAt
    }

    private tailrec fun loadActivitiesFromFolderRec(
        activitiesJob: Job,
        folderProxy: File,
        userDrive: UserDrive,
        okHttpClient: OkHttpClient,
        cursor: String? = folderProxy.cursor,
        returnResponse: ArrayMap<Int, FileAction> = arrayMapOf(),
    ): Map<out Int, FileAction> {
        val realm = folderProxy.realm
        val apiResponse = ApiRepository.getListingFiles(
            okHttpClient = okHttpClient,
            driveId = userDrive.driveId,
            parentId = folderProxy.id,
            cursor = cursor,
            order = File.SortType.NAME_AZ,
        )

        if (!apiResponse.isSuccess()) return returnResponse

        activitiesJob.ensureActive()

        val apiResponseData = apiResponse.data

        if (apiResponseData != null && apiResponseData.actions.isNotEmpty()) {
            val actionsFiles = apiResponseData.actionsFiles.associateBy(File::id)
            apiResponseData.actions.forEach { fileActivity ->
                fileActivity.applyFileAction(realm, actionsFiles, returnResponse, folderProxy)
            }
        }

        if (apiResponse.responseAt > 0L) {
            FileController.updateFile(folderProxy.id, realm) { file ->
                file.responseAt = apiResponse.responseAt
                apiResponse.cursor?.let { file.cursor = it }
            }
        } else {
            Sentry.withScope { scope ->
                scope.setExtra("data", apiResponse.toString())
                Sentry.captureMessage("response at is null")
            }
        }

        return if (apiResponse.hasMore && apiResponse.cursor != null) {
            // Loading the next page, then the cursor is required
            loadActivitiesFromFolderRec(
                activitiesJob = activitiesJob,
                folderProxy = folderProxy,
                cursor = apiResponse.cursor,
                userDrive = userDrive,
                okHttpClient = okHttpClient,
                returnResponse = returnResponse
            )
        } else {
            returnResponse
        }
    }

    private fun FileAction.applyFileAction(
        realm: Realm,
        actionFiles: Map<Int, File>,
        returnResponse: ArrayMap<Int, FileAction>,
        currentFolder: File,
    ) {
        val actionFile = actionFiles[fileId]
        when (action) {
            FileActivityType.FILE_DELETE,
            FileActivityType.FILE_MOVE_OUT,
            FileActivityType.FILE_TRASH -> {
                // We used to have this condition, but it doesn't exist on the ios side, according to commit it was an api fix.
                // returnResponse[fileId]?.createdAt?.time == createdAt.time
                if (returnResponse[fileId] == null) {
                    FileController.getParentFile(fileId = fileId, realm = realm)?.let { localFolder ->
                        if (localFolder.id != currentFolder.id) return@let

                        if (action == FileActivityType.FILE_MOVE_OUT) {
                            FileController.updateFile(localFolder.id, realm) { it.children.remove(actionFile) }
                        } else {
                            FileController.removeFile(fileId, customRealm = realm, recursive = false)
                        }
                    }

                    returnResponse[fileId] = this
                }
            }
            FileActivityType.FILE_CREATE,
            FileActivityType.FILE_MOVE_IN,
            FileActivityType.FILE_RESTORE -> {
                if (returnResponse[fileId] == null && actionFile != null) {
                    if (actionFile.isImporting()) MqttClientWrapper.start(actionFile.externalImport?.id)
                    realm.where(File::class.java).equalTo(File::id.name, currentFolder.id).findFirst()?.let { realmFolder ->
                        if (!realmFolder.children.contains(actionFile)) {
                            realm.executeTransaction { realmFolder.children.add(actionFile) }
                        } else {
                            FileController.updateFileFromActivity(realm, actionFile, realmFolder.id)
                        }
                        returnResponse[fileId] = this
                    }
                }
            }
            FileActivityType.COLLABORATIVE_FOLDER_CREATE,
            FileActivityType.COLLABORATIVE_FOLDER_DELETE,
            FileActivityType.COLLABORATIVE_FOLDER_UPDATE,
            FileActivityType.FILE_FAVORITE_CREATE,
            FileActivityType.FILE_FAVORITE_REMOVE,
            FileActivityType.FILE_RENAME,
            FileActivityType.FILE_CATEGORIZE,
            FileActivityType.FILE_UNCATEGORIZE,
            FileActivityType.FILE_COLOR_UPDATE,
            FileActivityType.FILE_COLOR_DELETE,
            FileActivityType.FILE_SHARE_CREATE,
            FileActivityType.FILE_SHARE_DELETE,
            FileActivityType.FILE_SHARE_UPDATE,
            FileActivityType.FILE_UPDATE -> {
                if (returnResponse[fileId] == null) {
                    if (actionFile == null) {
                        FileController.removeFile(fileId, customRealm = realm, recursive = false)
                    } else {
                        FileController.updateFileFromActivity(realm, actionFile, currentFolder.id)
                    }
                    returnResponse[fileId] = this
                }
            }
            else -> Unit
        }
    }

    data class FolderFilesProviderArgs(
        val folderId: FileId,
        val isFirstPage: Boolean = true,
        val order: File.SortType = File.SortType.NAME_AZ,
        val realm: Realm? = null,
        val sourceRestrictionType: SourceRestrictionType = SourceRestrictionType.UNRESTRICTED,
        val userDrive: UserDrive = UserDrive(),
        val withChildren: Boolean = true,
    )

    data class FolderFilesProviderResult(
        val folder: File,
        val folderFiles: ArrayList<File>,
        val isComplete: Boolean,
    )

    enum class SourceRestrictionType {
        UNRESTRICTED,
        ONLY_FROM_LOCAL,
        ONLY_FROM_REMOTE,
    }
}
