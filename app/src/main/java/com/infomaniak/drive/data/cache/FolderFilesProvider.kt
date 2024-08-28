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
    private const val MIN_VERSION_CODE = 4_03_002_01

    private val minDateToIgnoreCache = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.timeInMillis / 1000 // 3 month

    fun getFiles(folderFilesProviderArgs: FolderFilesProviderArgs): FolderFilesProviderResult? {
        val realm = folderFilesProviderArgs.realm ?: FileController.getRealmInstance(folderFilesProviderArgs.userDrive)

        val folderProxy = FileController.getFileById(realm, folderFilesProviderArgs.folderId)
        val sourceRestrictionType = folderFilesProviderArgs.sourceRestrictionType
        val needToLoadFromRemote = needToLoadFromRemote(sourceRestrictionType, folderProxy)

        val files = when {
            needToLoadFromRemote && sourceRestrictionType != SourceRestrictionType.ONLY_FROM_LOCAL -> {
                loadFromRemote(realm, folderProxy, folderFilesProviderArgs)
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

    fun loadSharedWithMeFiles(
        folderFilesProviderArgs: FolderFilesProviderArgs,
        onRecursionStart: (() -> Unit)? = null,
    ) = with(folderFilesProviderArgs) {
        val block: (Realm) -> Unit = { realm ->
            val rootFolder = File(id = FileController.SHARED_WITH_ME_FILE_ID, name = "/")
            val okHttpClient = runBlocking { AccountUtils.getHttpClient(userDrive.userId) }
            loadSharedWithMeFilesRec(
                realm = realm,
                okHttpClient = okHttpClient,
                folderFilesProviderArgs = this,
                isRoot = folderId == ROOT_ID,
                rootFolder = rootFolder,
                onRecursionStart = onRecursionStart,
            )
        }
        realm?.let(block) ?: FileController.getRealmInstance(userDrive).use(block)
    }

    private tailrec fun loadSharedWithMeFilesRec(
        realm: Realm,
        okHttpClient: OkHttpClient,
        isRoot: Boolean,
        folderFilesProviderArgs: FolderFilesProviderArgs,
        cursor: String? = null,
        rootFolder: File? = null,
        onRecursionStart: (() -> Unit)? = null,
    ) {
        val folderId = if (isRoot) FileController.SHARED_WITH_ME_FILE_ID else folderFilesProviderArgs.folderId
        val apiResponse = if (isRoot) {
            ApiRepository.getSharedWithMeFiles(order = folderFilesProviderArgs.order, cursor = cursor)
        } else {
            ApiRepository.getFolderFiles(
                okHttpClient = okHttpClient,
                driveId = folderFilesProviderArgs.userDrive.driveId,
                parentId = folderFilesProviderArgs.folderId,
                cursor = cursor,
                order = folderFilesProviderArgs.order,
            )
        }
        val folderProxy = FileController.getFileById(realm, folderId)

        fun saveFiles() = saveRemoteFiles(
            realm = realm,
            localFolderProxy = folderProxy,
            remoteFolder = if (folderFilesProviderArgs.folderId == ROOT_ID) rootFolder else folderProxy,
            apiResponse = apiResponse,
            isFirstPage = cursor == null,
            isCompleteFolder = !apiResponse.hasMore
        )

        when {
            apiResponse.hasMoreAndCursorExists -> {
                saveFiles()
                if (cursor == null) onRecursionStart?.invoke()
                loadSharedWithMeFilesRec(
                    realm = realm,
                    okHttpClient = okHttpClient,
                    isRoot = isRoot,
                    folderFilesProviderArgs = folderFilesProviderArgs,
                    cursor = apiResponse.cursor,
                )
            }
            apiResponse.data?.isNotEmpty() == true -> {
                saveFiles()
            }
        }
    }

    fun getCloudStorageFiles(
        realm: Realm,
        folderId: Int,
        userDrive: UserDrive,
        sortType: File.SortType,
        isFirstPage: Boolean = true,
        transaction: (files: ArrayList<File>) -> Unit,
        okHttpClient: OkHttpClient? = null,
    ) {
        val folderProxy = FileController.getFileById(realm, folderId)
        val folderFilesProviderArgs = FolderFilesProviderArgs(
            folderId = folderId,
            isFirstPage = isFirstPage,
            order = sortType,
            realm = realm,
            sourceRestrictionType = ONLY_FROM_REMOTE,
            userDrive = userDrive,
        )
        val currentOkHttpClient = okHttpClient ?: runBlocking { AccountUtils.getHttpClient(userDrive.userId) }
        val folderFilesProviderResult = loadCloudStorageFromRemote(
            realm = realm,
            folderProxy = folderProxy,
            folderFilesProviderArgs = folderFilesProviderArgs,
            okHttpClient = currentOkHttpClient,
        )

        transaction(folderFilesProviderResult?.folderFiles ?: arrayListOf())

        if (folderFilesProviderResult?.isComplete == false) getCloudStorageFiles(
            realm = realm,
            folderId = folderId,
            userDrive = userDrive,
            sortType = sortType,
            isFirstPage = false,
            transaction = transaction,
            okHttpClient = currentOkHttpClient,
        )
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
    ): FolderFilesProviderResult? = with(Dispatchers.IO) {

        val userDrive = folderFilesProviderArgs.userDrive
        val (okHttpClient, driveId) = runBlocking { AccountUtils.getHttpClient(userDrive.userId) } to userDrive.driveId

        val apiResponse = when {
            folderFilesProviderArgs.folderId == ROOT_ID -> {
                ApiRepository.getFolderFiles(
                    okHttpClient = okHttpClient,
                    driveId = driveId,
                    parentId = folderFilesProviderArgs.folderId,
                    cursor = if (folderFilesProviderArgs.isFirstPage) null else folderProxy?.cursor,
                    order = folderFilesProviderArgs.order,
                )

            }
            else -> {
                ApiRepository.getListingFiles(
                    okHttpClient = okHttpClient,
                    driveId = driveId,
                    parentId = folderFilesProviderArgs.folderId,
                    cursor = if (folderFilesProviderArgs.isFirstPage) null else folderProxy?.cursor,
                    order = folderFilesProviderArgs.order,
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
        }

        ensureActive()

        handleRemoteFiles(realm, apiResponse, folderFilesProviderArgs, folderProxy)
    }

    private fun loadCloudStorageFromRemote(
        realm: Realm,
        folderProxy: File?,
        folderFilesProviderArgs: FolderFilesProviderArgs,
        okHttpClient: OkHttpClient,
    ): FolderFilesProviderResult? = with(Dispatchers.IO) {
        val userDrive = folderFilesProviderArgs.userDrive

        val apiResponse = when {
            folderFilesProviderArgs.folderId == ROOT_ID && userDrive.sharedWithMe -> {
                ApiRepository.getSharedWithMeFiles(
                    order = folderFilesProviderArgs.order,
                    cursor = if (folderFilesProviderArgs.isFirstPage) null else folderProxy?.cursor,
                )
            }
            else -> {
                ApiRepository.getFolderFiles(
                    okHttpClient = okHttpClient,
                    driveId = userDrive.driveId,
                    parentId = folderFilesProviderArgs.folderId,
                    cursor = if (folderFilesProviderArgs.isFirstPage) null else folderProxy?.cursor,
                    order = folderFilesProviderArgs.order,
                )
            }
        }

        ensureActive()

        handleRemoteFiles(realm, apiResponse, folderFilesProviderArgs, folderProxy)
    }

    private fun handleRemoteFiles(
        realm: Realm,
        apiResponse: CursorApiResponse<List<File>>,
        folderFilesProviderArgs: FolderFilesProviderArgs,
        folderProxy: File?,
    ): FolderFilesProviderResult? {
        val userDrive = folderFilesProviderArgs.userDrive
        val apiResponseData = apiResponse.data
        val folderWithChildren = folderFilesProviderArgs.withChildren

        val localFolder = folderProxy?.freeze()
            ?: ApiRepository.getFileDetails(File(id = folderFilesProviderArgs.folderId, driveId = userDrive.driveId)).data
            ?: return null

        return when {
            apiResponseData != null -> {
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
                || folderProxy.versionCode <= MIN_VERSION_CODE
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
            apiResponseData.actions.asReversed().forEach { fileActivity ->
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

        return if (apiResponse.hasMoreAndCursorExists) {
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
                    removeAction(realm, currentFolder, actionFile)
                    returnResponse[fileId] = this
                }
            }
            else -> {
                if (returnResponse[fileId] == null && actionFile?.id != currentFolder.id) {
                    upsertAction(realm, currentFolder, actionFile)
                    returnResponse[fileId] = this
                }
            }
        }
    }

    private fun FileAction.removeAction(realm: Realm, currentFolder: File, actionFile: File?) {
        FileController.getParentFile(fileId = fileId, realm = realm)?.let { localFolder ->
            if (localFolder.id != currentFolder.id) return@let

            if (action == FileActivityType.FILE_MOVE_OUT) {
                FileController.updateFile(localFolder.id, realm) { it.children.remove(actionFile) }
            } else {
                FileController.removeFile(fileId, customRealm = realm, recursive = false)
            }
        }
    }

    private fun FileAction.upsertAction(realm: Realm, currentFolder: File, actionFile: File?) {
        if (actionFile == null) {
            FileController.removeFile(fileId, customRealm = realm, recursive = false)
        } else {
            if (actionFile.isImporting()) MqttClientWrapper.start(actionFile.externalImport?.id)
            FileController.upsertActionFile(realm, currentFolder.id, actionFile)
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
