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
package com.infomaniak.drive.data.cache

import android.content.Context
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.setColor
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.Type
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.FileActivity.FileActivityType
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import io.realm.*
import io.realm.kotlin.oneOf
import io.sentry.Sentry
import kotlinx.android.parcel.RawValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.*
import kotlin.collections.ArrayList

object FileController {
    private const val REALM_DB_FILE = "kDrive-%s-%s.realm"
    private const val REALM_DB_SHARES_WITH_ME = "kDrive-%s-%s-shares.realm"

    const val FAVORITES_FILE_ID = -1
    const val MY_SHARES_FILE_ID = -2
    const val RECENT_CHANGES_FILE_ID = -4
    private const val PICTURES_FILE_ID = -3

    private val FAVORITES_FILE = File(FAVORITES_FILE_ID, name = "Favoris")
    private val MY_SHARES_FILE = File(MY_SHARES_FILE_ID, name = "My Shares")
    private val PICTURES_FILE = File(PICTURES_FILE_ID, name = "Pictures")
    private val RECENT_CHANGES_FILE = File(RECENT_CHANGES_FILE_ID, name = "Recent changes")

    private val minDateToIgnoreCache = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.timeInMillis / 1000 // 3 month

    private fun getFileById(realm: Realm, fileId: Int) = realm.where(File::class.java).equalTo("id", fileId).findFirst()

    // https://github.com/realm/realm-java/issues/1862
    fun emptyList(realm: Realm): RealmResults<File> = realm.where(File::class.java).alwaysFalse().findAll()

    fun getParentFile(fileId: Int, userDrive: UserDrive? = null, realm: Realm? = null): File? {
        val block: (Realm) -> File? = { currentRealm ->
            getFileById(currentRealm, fileId)?.localParent?.let { parents ->
                if (parents.count() == 1) parents.firstOrNull()
                else parents.firstOrNull { it.id > 0 }
            }?.let { parent ->
                currentRealm.copyFromRealm(parent, 0)
            }
        }
        return realm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    fun generateAndSavePath(fileId: Int, userDrive: UserDrive): String {
        return getRealmInstance(userDrive).use { realm ->
            getFileById(realm, fileId)?.let { file ->
                if (file.path.isEmpty()) {
                    val generatedPath = generatePath(file, userDrive)
                    if (generatedPath.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            savePath(userDrive, fileId, generatedPath)
                        }

                    }
                    generatedPath
                } else file.path
            } ?: ""
        }
    }

    private fun savePath(userDrive: UserDrive, fileId: Int, generatedPath: String) {
        getRealmInstance(userDrive).use { realm ->
            getFileById(realm, fileId)?.let { file ->
                realm.executeTransaction {
                    if (file.isValid) file.path = generatedPath
                }
            }
        }
    }

    private fun generatePath(file: File, userDrive: UserDrive): String {
        // id > 0 to exclude other root parents, home root has priority
        val folder = file.localParent?.createSnapshot()?.firstOrNull { it.id > 0 }
        return when {
            folder == null -> ""
            folder.id == Utils.ROOT_ID -> (userDrive.driveName ?: "") + "/${file.name}"
            else -> generatePath(folder, userDrive) + "/${file.name}"
        }
    }

    fun getFileProxyById(fileId: Int, userDrive: UserDrive? = null, customRealm: Realm? = null): File? {
        val block: (Realm) -> File? = { realm ->
            realm.where(File::class.java).equalTo(File::id.name, fileId).findFirst()
        }
        return customRealm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    fun getFileById(fileId: Int, userDrive: UserDrive? = null): File? {
        return getRealmInstance(userDrive).use { realm ->
            realm.where(File::class.java).equalTo(File::id.name, fileId).findFirst()?.let {
                realm.copyFromRealm(it, 1)
            }
        }
    }

    fun removeFile(
        fileId: Int,
        keepFileCaches: ArrayList<Int> = arrayListOf(),
        keepFiles: ArrayList<Int> = arrayListOf(),
        customRealm: Realm? = null,
        recursive: Boolean = true,
    ) {
        val block: (Realm) -> Unit? = { realm ->
            getFileById(realm, fileId)?.let { file ->
                if (recursive) {
                    file.children.createSnapshot().forEach {
                        if (!keepFiles.contains(it.id)) removeFile(it.id, keepFileCaches, keepFiles, realm)
                    }
                }
                try {
                    if (!keepFileCaches.contains(fileId)) file.deleteCaches(Realm.getApplicationContext()!!)
                    if (!keepFiles.contains(fileId)) realm.executeTransaction { if (file.isValid) file.deleteFromRealm() }
                } catch (exception: Exception) {
                    Sentry.withScope { scope ->
                        scope.setExtra("with custom realm", "${customRealm != null}")
                        scope.setExtra("recursive", "$recursive")
                        Sentry.captureException(exception)
                    }
                }
            }
        }
        customRealm?.let(block) ?: getRealmInstance().use(block)
    }

    fun renameFile(file: File, newName: String, realm: Realm? = null): ApiResponse<CancellableAction> {
        val apiResponse = ApiRepository.renameFile(file, newName)
        if (apiResponse.isSuccess()) {
            updateFile(file.id, realm) { localFile ->
                localFile.name = newName
            }
        }
        return apiResponse
    }

    fun updateFolderColor(file: File, color: String, realm: Realm? = null): ApiResponse<Boolean?> {
        return ApiRepository.updateFolderColor(file, color).also {
            if (it.isSuccess()) updateFile(file.id, realm) { localFile -> localFile.setColor(color) }
        }
    }

    fun deleteFile(
        file: File,
        realm: Realm? = null,
        userDrive: UserDrive? = null,
        context: Context,
        onSuccess: ((fileID: Int) -> Unit)? = null
    ): ApiResponse<CancellableAction> {
        val apiResponse = ApiRepository.deleteFile(file)
        if (apiResponse.isSuccess()) {
            file.deleteCaches(context)
            updateFile(file.id, realm, userDrive = userDrive) { localFile -> localFile.deleteFromRealm() }
            onSuccess?.invoke(file.id)
        }
        return apiResponse
    }

    fun updateFile(fileId: Int, realm: Realm? = null, userDrive: UserDrive? = null, transaction: (file: File) -> Unit) {
        try {
            val block: (Realm) -> Unit? = { currentRealm ->
                getFileById(currentRealm, fileId)?.let { file ->
                    currentRealm.executeTransaction {
                        if (file.isValid) transaction(file)
                    }
                }
            }
            realm?.let(block) ?: getRealmInstance(userDrive).use(block)
        } catch (exception: Exception) {
            Sentry.withScope { scope ->
                scope.setExtra("custom realm", "${realm != null}")
                Sentry.captureException(exception)
            }
        }
    }

    fun updateOfflineStatus(fileId: Int, isOffline: Boolean) {
        updateFile(fileId) { file ->
            file.isOffline = isOffline
        }
    }

    fun updateExistingFile(
        newFile: File,
        realm: Realm
    ) {
        getFileById(realm, newFile.id)?.let { localFile ->
            insertOrUpdateFile(realm, newFile, localFile)
        }
    }

    private fun insertOrUpdateFile(
        realm: Realm,
        newFile: File,
        oldFile: File? = null,
        moreTransaction: (() -> Unit)? = null
    ) {
        realm.executeTransaction {
            if (oldFile?.isUsable() == true) {
                newFile.isComplete = oldFile.isComplete
                newFile.children = oldFile.children
                newFile.responseAt = oldFile.responseAt
                newFile.isOffline = oldFile.isOffline
            }
            moreTransaction?.invoke()
            it.insertOrUpdate(newFile)
        }
    }

    private fun addChildren(realm: Realm, file: File, children: ArrayList<File>) {
        realm.executeTransaction {
            file.children.addAll(children)
        }
    }

    private fun addChild(realm: Realm, localFolder: File, file: File) {
        if (localFolder.children.find { it.id == file.id } == null) {
            realm.executeTransaction { localFolder.children.add(file) }
        }
    }

    fun saveFavoritesFiles(files: List<File>, replaceOldData: Boolean = false, realm: Realm? = null) {
        saveFiles(FAVORITES_FILE, files, replaceOldData, realm)
    }

    private fun saveMySharesFiles(files: ArrayList<File>, replaceOldData: Boolean) {
        val keepCaches = arrayListOf<Int>()
        val keepFiles = arrayListOf<Int>()
        getRealmInstance().use { realm ->
            files.forEachIndexed { index, file ->
                val offlineFile = file.getOfflineFile(Realm.getApplicationContext()!!)

                realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()?.let { oldFile ->
                    realm.executeTransaction {
                        file.children = oldFile.children
                        keepFiles.add(file.id)
                    }
                }

                if (offlineFile != null && file.isOfflineAndIntact(offlineFile)) {
                    files[index].isOffline = true
                    keepCaches.add(file.id)
                } else offlineFile?.delete()
            }

            if (replaceOldData) removeFile(MY_SHARES_FILE_ID, keepCaches, keepFiles, realm)
            saveFiles(MY_SHARES_FILE, files, replaceOldData, realm)
        }
    }

    private fun saveFiles(
        folder: File,
        files: List<File>,
        replaceOldData: Boolean = false,
        realm: Realm? = null
    ) {
        val block: (Realm) -> Unit = { currentRealm ->
            currentRealm.executeTransaction { realm ->
                val newMySharesFolder = if (replaceOldData) {
                    realm.copyToRealmOrUpdate(folder)
                } else {
                    currentRealm.where(File::class.java).equalTo(File::id.name, folder.id).findFirst()
                        ?: realm.copyToRealmOrUpdate(folder)
                }
                newMySharesFolder?.children?.addAll(realm, files)
            }
        }

        realm?.let(block) ?: getRealmInstance().use(block)
    }

    private fun getDriveFileName(userDrive: UserDrive): String {
        val realmDb = if (userDrive.sharedWithMe) REALM_DB_SHARES_WITH_ME else REALM_DB_FILE
        return realmDb.format(userDrive.userId, userDrive.driveId)
    }

    fun getRealmInstance(userDrive: UserDrive? = null): Realm {
        return Realm.getInstance(getRealmConfiguration(getDriveFileName(userDrive ?: UserDrive())))
    }

    private fun getRealmConfiguration(dbName: String): RealmConfiguration {
        return RealmConfiguration.Builder()
            .schemaVersion(FileMigration.bddVersion) // Must be bumped when the schema changes
            .migration(FileMigration())
            .modules(RealmModules.LocalFilesModule())
            .name(dbName)
            .build()
    }

    /**
     * Delete all drive data cache for ne user
     * @param userID User ID
     * @param driveID Drive ID (null if all user drive)
     */
    fun deleteUserDriveFiles(userID: Int, driveID: Int? = null) {
        val filesDir = Realm.getApplicationContext()!!.filesDir
        filesDir.listFiles()?.forEach { file ->
            val match = Regex("(\\d+)-(\\d+)").find(file.name)
            match?.destructured?.let {
                val (fileUserId, fileDriveId) = it
                if (fileUserId.toInt() == userID && (driveID == null || fileDriveId.toInt() == driveID)) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
        }
    }

    fun getFilesFromCache(folderID: Int, userDrive: UserDrive? = null, order: SortType = SortType.NAME_AZ): ArrayList<File> {
        return getRealmInstance(userDrive).use { currentRealm ->
            currentRealm
                .where(File::class.java)
                .equalTo(File::id.name, folderID)
                .findFirst()?.children?.where()?.getSortQueryByOrder(order)?.findAll()?.let { children ->
                    ArrayList(currentRealm.copyFromRealm(children, 0))
                }
        } ?: ArrayList()
    }

    fun getFileDetails(fileId: Int, userDrive: UserDrive): File? {
        return getRealmInstance(userDrive).use { realm ->
            val apiResponse = ApiRepository.getFileDetails(File(id = fileId, driveId = userDrive.driveId))
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { remoteFile ->
                    insertOrUpdateFile(realm, remoteFile, getFileProxyById(fileId, customRealm = realm))
                    remoteFile
                }
            } else {
                null
            }
        }
    }

    fun getCloudStorageFiles(
        parentId: Int,
        userDrive: UserDrive,
        sortType: SortType,
        page: Int = 1,
        transaction: (files: ArrayList<File>) -> Unit
    ) {
        val filesFromCacheOrDownload = getFilesFromCacheOrDownload(
            parentId = parentId,
            page = page,
            ignoreCache = true,
            order = sortType,
            userDrive = userDrive
        )
        val files = filesFromCacheOrDownload?.second ?: arrayListOf()
        transaction(files)
        if (files.size >= ApiRepository.PER_PAGE) getCloudStorageFiles(parentId, userDrive, sortType, page + 1, transaction)
    }

    suspend fun getMySharedFiles(
        userDrive: UserDrive,
        sortType: SortType,
        page: Int = 1,
        ignoreCloud: Boolean = false,
        transaction: (files: ArrayList<File>, isComplete: Boolean) -> Unit
    ) {
        if (ignoreCloud) {
            transaction(getFilesFromCache(MY_SHARES_FILE_ID, userDrive, sortType), true)
        } else {
            val apiResponse = ApiRepository.getMySharedFiles(
                KDriveHttpClient.getHttpClient(userDrive.userId), userDrive.driveId, sortType.order, sortType.orderBy, page
            )
            if (apiResponse.isSuccess()) {
                val apiResponseData = apiResponse.data
                when {
                    apiResponseData.isNullOrEmpty() -> transaction(arrayListOf(), true)
                    apiResponseData.size < ApiRepository.PER_PAGE -> {
                        saveMySharesFiles(apiResponseData, page == 1)
                        transaction(apiResponseData, true)
                    }
                    else -> {
                        saveMySharesFiles(apiResponseData, page == 1)
                        transaction(apiResponseData, false)
                        getMySharedFiles(userDrive, sortType, page + 1, false, transaction)
                    }
                }
            } else if (page == 1) transaction(getFilesFromCache(MY_SHARES_FILE_ID, userDrive, sortType), true)
        }
    }

    suspend fun cloudStorageSearch(
        userDrive: UserDrive,
        query: String,
        onResponse: (files: ArrayList<File>) -> Unit,
        page: Int = 1
    ) {
        val order = SortType.NAME_AZ
        val apiResponse = ApiRepository.searchFiles(
            userDrive.driveId,
            query,
            order.order,
            order.orderBy,
            page,
            okHttpClient = KDriveHttpClient.getHttpClient(userDrive.userId)
        )

        if (apiResponse.isSuccess()) {
            when {
                apiResponse.data?.isNullOrEmpty() == true -> onResponse(arrayListOf())
                apiResponse.data!!.size < ApiRepository.PER_PAGE -> onResponse(apiResponse.data ?: arrayListOf())
                else -> {
                    onResponse(apiResponse.data ?: arrayListOf())
                    cloudStorageSearch(userDrive, query, onResponse, page + 1)
                }
            }
        } else if (page == 1) {
            onResponse(searchFiles(query, order, userDrive))
        }
    }

    fun getActivities(): ArrayList<FileActivity> {
        val activityResults = arrayListOf<FileActivity>()
        getRealmInstance().use { realm ->
            realm.where(FileActivity::class.java)
                .sort(FileActivity::createdAt.name, Sort.DESCENDING)
                .findAll()?.forEach { fileActivity ->
                    fileActivity.userId?.let { userId ->
                        activityResults.add(realm.copyFromRealm(fileActivity, 1).apply {
                            user = DriveInfosController.getUsers(arrayListOf(userId)).firstOrNull()
                        })
                    }
                }
        }
        return activityResults
    }

    fun getPicturesDrive(customRealm: Realm? = null): ArrayList<File> {
        val operation: (Realm) -> ArrayList<File> = { realm ->
            realm.where(File::class.java).equalTo(File::id.name, PICTURES_FILE_ID).findFirst()?.let { picturesFolder ->
                realm.copyFromRealm(picturesFolder.children, 0) as ArrayList<File>
            } ?: arrayListOf()
        }
        return customRealm?.let(operation) ?: getRealmInstance().use(operation)
    }

    fun getRecentChanges(): ArrayList<File> {
        return getRealmInstance().use { realm ->
            realm.where(File::class.java).equalTo(File::id.name, RECENT_CHANGES_FILE_ID).findFirst()?.let { folder ->
                ArrayList(realm.copyFromRealm(folder.children, 0))
            } ?: arrayListOf()
        }
    }

    fun storeRecentChanges(files: ArrayList<File>, isFirstPage: Boolean = false) {
        getRealmInstance().use {
            it.executeTransaction { realm ->
                val folder = realm.where(File::class.java).equalTo(File::id.name, RECENT_CHANGES_FILE_ID).findFirst()
                    ?: realm.copyToRealm(RECENT_CHANGES_FILE)
                if (isFirstPage) {
                    folder.children = RealmList()
                }
                files.forEach { file ->
                    realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()?.let { realmFile ->
                        keepOldLocalFilesData(realmFile, file)
                    }
                    folder.children.add(file)
                }
            }
        }
    }

    fun storeFileActivities(fileActivities: ArrayList<FileActivity>) {
        getRealmInstance().use { realm ->
            realm.beginTransaction()
            fileActivities.forEach { fileActivity ->
                fileActivity.userId = fileActivity.user?.id
                fileActivity.file?.let { file ->
                    realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()
                }?.let { localFile ->
                    fileActivity.file?.let { keepOldLocalFilesData(localFile, it) }
                }
                realm.insertOrUpdate(fileActivity)
            }
            realm.commitTransaction()
        }
    }

    fun storePicturesDrive(pictures: List<File>, isFirstPage: Boolean = false, customRealm: Realm? = null) {
        val block: (Realm) -> Unit = {
            it.executeTransaction { realm ->
                val picturesFolder = realm.where(File::class.java).equalTo(File::id.name, PICTURES_FILE_ID).findFirst()
                    ?: realm.copyToRealm(PICTURES_FILE)
                if (isFirstPage) picturesFolder.children = RealmList()
                picturesFolder.children.addAll(realm, pictures)
            }
        }
        customRealm?.let(block) ?: getRealmInstance().use(block)
    }

    private fun RealmList<File>.addAll(realm: Realm, files: List<File>) {
        files.forEach { file ->
            realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()?.also { managedFile ->
                keepOldLocalFilesData(managedFile, file)
            }
            add(file)
        }
    }

    fun removeOrphanAndActivityFiles(customRealm: Realm? = null) {
        val block: (Realm) -> Unit = { realm ->
            realm.executeTransaction {
                realm.where(FileActivity::class.java).findAll().deleteAllFromRealm()
            }
            removeOrphanFiles(realm)
        }
        customRealm?.let(block) ?: getRealmInstance().use(block)
    }

    fun removeOrphanFiles(customRealm: Realm? = null) {
        val block: (Realm) -> Unit = { realm ->
            realm.executeTransaction {
                realm.where(File::class.java)
                    .greaterThan(File::id.name, Utils.ROOT_ID)
                    .isEmpty(File::localParent.name)
                    .findAll().deleteAllFromRealm()
            }
        }
        customRealm?.let(block) ?: getRealmInstance().use(block)
    }

    fun getRealmLiveFiles(
        parentId: Int,
        realm: Realm,
        order: SortType?,
        withVisibilitySort: Boolean = true,
        isFavorite: Boolean = false
    ): RealmResults<File> {
        realm.refresh()
        return getRealmLiveSortedFiles(getFileById(realm, parentId), order, withVisibilitySort, isFavorite = isFavorite)
            ?: emptyList(realm)
    }

    fun getFilesFromCacheOrDownload(
        parentId: Int,
        page: Int,
        ignoreCache: Boolean = false,
        ignoreCloud: Boolean = false,
        order: SortType = SortType.NAME_AZ,
        userDrive: UserDrive?,
        customRealm: Realm? = null,
        withChildren: Boolean = true
    ): Pair<File, ArrayList<File>>? {

        fun hasDuplicatesFiles(query: RealmQuery<File>): Boolean {
            return query.count() != query.distinct(File::id.name).count()
        }

        val operation: (Realm) -> Pair<File, ArrayList<File>>? = { realm ->
            var result: Pair<File, ArrayList<File>>? = null
            val folderProxy = getFileById(realm, parentId)
            val localFolderWithoutChildren = folderProxy?.let { realm.copyFromRealm(it, 1) }
            val hasDuplicatesFiles = folderProxy?.children?.where()?.let(::hasDuplicatesFiles) ?: false

            val needToDownload = ignoreCache
                    || folderProxy == null
                    || folderProxy.children.isNullOrEmpty()
                    || !folderProxy.isComplete
                    || hasDuplicatesFiles
                    || minDateToIgnoreCache >= folderProxy.responseAt

            if (needToDownload && !ignoreCloud) {
                result = downloadAndSaveFiles(
                    currentRealm = realm,
                    localFolder = folderProxy,
                    localFolderWithoutChildren = localFolderWithoutChildren,
                    order = order,
                    page = page,
                    parentId = parentId,
                    userDrive = userDrive,
                    withChildren = withChildren
                )
            } else if (page == 1 && localFolderWithoutChildren != null) {
                val localSortedFolderFiles = if (withChildren) getLocalSortedFolderFiles(folderProxy, order) else arrayListOf()
                result = (localFolderWithoutChildren to localSortedFolderFiles)
            }
            result
        }
        return customRealm?.let(operation) ?: getRealmInstance(userDrive).use(operation)
    }

    fun getFilesFromIdList(realm: Realm, idList: Array<Int>, order: SortType = SortType.NAME_AZ): RealmResults<File>? {
        return realm.where(File::class.java)
            .oneOf(File::id.name, idList)
            .getSortQueryByOrder(order)
            .findAll()
    }

    private fun downloadAndSaveFiles(
        currentRealm: Realm,
        localFolder: File?,
        localFolderWithoutChildren: File?,
        order: SortType,
        page: Int,
        parentId: Int,
        userDrive: UserDrive?,
        withChildren: Boolean
    ): Pair<File, ArrayList<File>>? {
        var result: Pair<File, ArrayList<File>>? = null
        val okHttpClient: OkHttpClient
        val driveId: Int
        if (userDrive == null) {
            okHttpClient = HttpClient.okHttpClient
            driveId = AccountUtils.currentDriveId
        } else {
            okHttpClient = runBlocking { KDriveHttpClient.getHttpClient(userDrive.userId) }
            driveId = userDrive.driveId
        }
        val apiResponse = ApiRepository.getFileListForFolder(okHttpClient, driveId, parentId, page, order)

        if (apiResponse.isSuccess()) {
            apiResponse.data?.let { remoteFolder ->
                val apiChildrenRealmList = remoteFolder.children
                val apiChildren = ArrayList<File>(apiChildrenRealmList.toList())

                saveRemoteFiles(localFolder, remoteFolder, page, currentRealm, apiChildren, apiResponse)
                remoteFolder.children = RealmList()
                result = (remoteFolder to if (withChildren) apiChildren else arrayListOf())
            }
        } else if (page == 1 && localFolderWithoutChildren != null) {
            val localSortedFolderFiles = if (withChildren) getLocalSortedFolderFiles(localFolder, order) else arrayListOf()
            result = (localFolderWithoutChildren to localSortedFolderFiles)
        }
        return result
    }

    private fun saveRemoteFiles(
        localFolder: File?,
        remoteFolder: @RawValue File,
        page: Int,
        currentRealm: Realm,
        apiChildren: ArrayList<File>,
        apiResponse: ApiResponse<File>
    ) {
        // Restore same children data
        keepSubFolderChildren(localFolder?.children, remoteFolder.children)
        // Save to realm
        if (localFolder?.children.isNullOrEmpty() || page == 1) {
            saveRemoteFolder(currentRealm, remoteFolder, apiChildren.size, apiResponse.responseAt)
        } else {
            localFolder?.let { it ->
                addChildren(currentRealm, it, apiChildren)
                saveRemoteFolder(currentRealm, it, apiChildren.size, apiResponse.responseAt)
            }
        }
    }

    private fun keepSubFolderChildren(localFolderChildren: List<File>?, remoteFolderChildren: List<File>) {
        localFolderChildren?.filter { !it.children.isNullOrEmpty() || it.isOffline }?.map { oldFile ->
            remoteFolderChildren.find { it.id == oldFile.id }?.apply {
                if (oldFile.isFolder()) children = oldFile.children
                isOffline = oldFile.isOffline
            }
        }
    }

    private fun saveRemoteFolder(
        currentRealm: Realm,
        remoteFolder: File,
        childrenSize: Int,
        responseAt: Long
    ) {
        insertOrUpdateFile(currentRealm, remoteFolder) {
            if (childrenSize < ApiRepository.PER_PAGE) remoteFolder.isComplete = true
            remoteFolder.responseAt = responseAt
        }
    }

    private fun getRealmLiveSortedFiles(
        localFolder: File?,
        order: SortType?,
        withVisibilitySort: Boolean = true,
        localChildren: RealmResults<File>? = null,
        isFavorite: Boolean = false
    ): RealmResults<File>? {
        val children = localChildren ?: localFolder?.children
        return children?.where()
            ?.apply {
                order?.let {
                    getSortQueryByOrder(it)
                    if (isFavorite) equalTo(File::isFavorite.name, true)
                    if (withVisibilitySort) sort(File::visibility.name, Sort.DESCENDING)
                    sort(File::type.name, Sort.ASCENDING)
                    distinct(File::id.name)
                }
            }?.findAll()
    }

    private fun getLocalSortedFolderFiles(
        localFolder: File?,
        order: SortType,
        localChildren: RealmResults<File>? = null,
        currentRealm: Realm? = null
    ): ArrayList<File> {
        val files = getRealmLiveSortedFiles(localFolder, order, localChildren = localChildren)?.let { realmFiles ->
            localFolder?.realm?.copyFromRealm(realmFiles, 1) ?: currentRealm?.copyFromRealm(realmFiles, 1)
        }
        return files?.let { ArrayList(it) } ?: arrayListOf()
    }

    fun getFolderActivities(folder: File, page: Int, userDrive: UserDrive? = null): Map<out Int, FileActivity> {
        return getRealmInstance(userDrive).use { realm ->
            getFolderActivitiesRec(realm, folder, page, userDrive)
        }
    }

    private fun getFolderActivitiesRec(
        realm: Realm,
        folder: File,
        page: Int,
        userDrive: UserDrive? = null
    ): Map<out Int, FileActivity> {
        val okHttpClient = runBlocking {
            userDrive?.userId?.let { KDriveHttpClient.getHttpClient(it) } ?: HttpClient.okHttpClient
        }
        val returnResponse = arrayMapOf<Int, FileActivity>()
        val apiResponse = ApiRepository.getFileActivities(okHttpClient, folder, page)
        if (!apiResponse.isSuccess()) return returnResponse

        return if (apiResponse.data?.isNotEmpty() == true) {
            apiResponse.data?.forEach { fileActivity ->
                fileActivity.applyFileActivity(realm, returnResponse, folder)
            }

            if ((apiResponse.data?.size ?: 0) < ApiRepository.PER_PAGE) {
                if (apiResponse.responseAt > 0L) {
                    updateFile(folder.id, realm) { file -> file.responseAt = apiResponse.responseAt }
                } else {
                    Sentry.withScope { scope ->
                        scope.setExtra("data", apiResponse.toString())
                        Sentry.captureMessage("response at is null")
                    }
                }
                returnResponse

            } else returnResponse.apply { putAll(getFolderActivitiesRec(realm, folder, page + 1, userDrive)) }
        } else {
            if (apiResponse.responseAt > 0L) {
                updateFile(folder.id, realm) { file -> file.responseAt = apiResponse.responseAt }
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
        when (getAction()) {
            FileActivityType.FILE_DELETE,
            FileActivityType.FILE_MOVE_OUT,
            FileActivityType.FILE_TRASH -> {
                if (returnResponse[fileId] == null || returnResponse[fileId]?.createdAt?.time == createdAt.time) { // Api fix
                    getParentFile(fileId = fileId, realm = realm)?.let { parent ->
                        if (parent.id == currentFolder.id) removeFile(fileId, customRealm = realm, recursive = false)
                    }
                    returnResponse[fileId] = this
                }
            }
            FileActivityType.FILE_CREATE,
            FileActivityType.FILE_MOVE_IN,
            FileActivityType.FILE_RESTORE -> {
                if (returnResponse[fileId] == null && file != null) {
                    realm.where(File::class.java).equalTo(File::id.name, currentFolder.id).findFirst()?.let { realmFolder ->
                        if (!realmFolder.children.contains(file)) {
                            addChild(realm, realmFolder, file!!)
                        } else {
                            updateFileFromActivity(realm, this, realmFolder.id)
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
                    if (file == null) {
                        removeFile(fileId, customRealm = realm, recursive = false)
                    } else {
                        updateFileFromActivity(realm, this, currentFolder.id)
                    }
                    returnResponse[fileId] = this
                }
            }
            else -> Unit
        }
    }

    fun getOfflineFiles(
        order: SortType?,
        userDrive: UserDrive = UserDrive(),
        customRealm: Realm? = null
    ): RealmResults<File> {
        val block: (Realm) -> RealmResults<File> = { realm ->
            realm.where(File::class.java)
                .equalTo(File::isOffline.name, true)
                .notEqualTo(File::type.name, Type.FOLDER.value)
                .findAll()?.let { files ->
                    if (order == null) files
                    else getRealmLiveSortedFiles(localFolder = null, order = order, localChildren = files)
                } ?: emptyList(realm)
        }
        return customRealm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    fun searchFiles(
        query: String,
        order: SortType,
        userDrive: UserDrive = UserDrive(),
        customRealm: Realm? = null
    ): ArrayList<File> {
        val block: (Realm) -> ArrayList<File> = { currentRealm ->
            currentRealm.where(File::class.java).like(File::name.name, "*$query*").findAll()?.let { files ->
                getLocalSortedFolderFiles(null, order, files, currentRealm)
            } ?: arrayListOf()
        }
        return customRealm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    private fun updateFileFromActivity(realm: Realm, fileActivity: FileActivity, folderId: Int) {
        getFileProxyById(fileActivity.fileId, customRealm = realm)?.let { file ->
            insertOrUpdateFile(realm, fileActivity.file!!, file)
        } ?: also {
            realm.executeTransaction {
                realm.where(File::class.java).equalTo(File::id.name, folderId).findFirst()?.children?.add(fileActivity.file)
            }
        }
    }

    fun addFileTo(parentFolderID: Int, file: File, userDrive: UserDrive? = null) {
        getRealmInstance(userDrive).use { realm ->
            val localFolder = realm.where(File::class.java).equalTo(File::id.name, parentFolderID).findFirst()
            if (localFolder != null) {
                realm.executeTransaction {
                    localFolder.children.add(file)
                }
            }
        }
    }

    suspend fun createFolder(name: String, parentId: Int, onlyForMe: Boolean, userDrive: UserDrive?): ApiResponse<File> {
        val okHttpClient = userDrive?.userId?.let { KDriveHttpClient.getHttpClient(it) } ?: HttpClient.okHttpClient
        val driveId = userDrive?.driveId ?: AccountUtils.currentDriveId
        return ApiRepository.createFolder(okHttpClient, driveId, parentId, name, onlyForMe, false)
    }

    suspend fun createCommonFolder(name: String, forAllUsers: Boolean, userDrive: UserDrive?): ApiResponse<File> {
        val okHttpClient = userDrive?.userId?.let { KDriveHttpClient.getHttpClient(it) } ?: HttpClient.okHttpClient
        val driveId = userDrive?.driveId ?: AccountUtils.currentDriveId
        return ApiRepository.createTeamFolder(okHttpClient, driveId, name, forAllUsers)
    }

    private fun keepOldLocalFilesData(managedFile: File, remoteFile: File) {
        remoteFile.apply {
            children = managedFile.children
            isComplete = managedFile.isComplete
            responseAt = managedFile.responseAt
            isOffline = managedFile.isOffline
        }
    }

    private fun RealmQuery<File>.getSortQueryByOrder(order: SortType): RealmQuery<File> {
        return when (order) {
            SortType.NAME_AZ -> sort(File::nameNaturalSorting.name, Sort.ASCENDING)
            SortType.NAME_ZA -> sort(File::nameNaturalSorting.name, Sort.DESCENDING)
            SortType.OLDER -> sort(File::lastModifiedAt.name, Sort.ASCENDING)
            SortType.RECENT -> sort(File::lastModifiedAt.name, Sort.DESCENDING)
            SortType.OLDER_TRASHED -> sort(File::deletedAt.name, Sort.ASCENDING)
            SortType.RECENT_TRASHED -> sort(File::deletedAt.name, Sort.DESCENDING)
            SortType.SMALLER -> sort(File::size.name, Sort.ASCENDING)
            SortType.BIGGER -> sort(File::size.name, Sort.DESCENDING)
            // SortType.EXTENSION -> sort(File::convertedType.name, Sort.ASCENDING) // TODO implement
        }
    }
}
