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

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import io.realm.*
import kotlinx.android.parcel.RawValue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.*
import kotlin.collections.ArrayList

object FileController {
    private const val REALM_DB_FILE = "kDrive-%s-%s.realm"
    private const val REALM_DB_SHARES_WITH_ME = "kDrive-%s-%s-shares.realm"

    const val FAVORITES_FILE_ID = -1
    private const val MY_SHARES_FILE_ID = -2
    private const val PICTURES_FILE_ID = -3

    private val FAVORITES_FILE = File(FAVORITES_FILE_ID, name = "Favoris")
    private val MY_SHARES_FILE = File(MY_SHARES_FILE_ID, name = "My Shares")
    private val PICTURES_FILE = File(PICTURES_FILE_ID, name = "Pictures")

    private fun getFileById(realm: Realm, fileId: Int) = realm.where(File::class.java).equalTo("id", fileId).findFirst()

    fun getParentFile(fileId: Int, userDrive: UserDrive? = null): File? {
        return getRealmInstance(userDrive).use { realm ->
            getFileById(realm, fileId)?.localParent?.let { parents ->
                if (parents.count() == 1) parents.firstOrNull()
                else parents.firstOrNull { it.id > 0 }
            }?.let { parent ->
                realm.copyFromRealm(parent, 0)
            }
        }
    }

    fun getFileById(fileId: Int, userDrive: UserDrive? = null): File? {
        return getRealmInstance(userDrive).use { realm ->
            realm.where(File::class.java).equalTo(File::id.name, fileId).findFirst()?.let {
                realm.copyFromRealm(it, 1)
            }
        }
    }

    private fun removeFileCascade(
        fileId: Int,
        keepFileCaches: ArrayList<Int> = arrayListOf(),
        keepFiles: ArrayList<Int> = arrayListOf(),
        realm: Realm? = null
    ) {
        val block: (Realm) -> Unit = { currentRealm ->
            getFileById(currentRealm, fileId)?.let { file ->
                file.children.createSnapshot().forEach {
                    if (!keepFiles.contains(it.id)) removeFileCascade(it.id, keepFileCaches, keepFiles, realm)
                }
                if (!keepFileCaches.contains(fileId)) file.deleteCaches(Realm.getApplicationContext()!!)
                if (!keepFiles.contains(fileId) && file.isValid) currentRealm.executeTransaction { file.deleteFromRealm() }
            }
        }
        realm?.let(block) ?: Realm.getDefaultInstance()?.use(block)
    }

    fun updateFile(fileId: Int, realm: Realm? = null, userDrive: UserDrive = UserDrive(), transaction: (file: File) -> Unit) {
        val block: (Realm) -> Unit? = { currentRealm ->
            getFileById(currentRealm, fileId)?.let { file -> currentRealm.executeTransaction { transaction(file) } }
        }
        realm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    fun updateOfflineStatus(fileId: Int, isOffline: Boolean) {
        updateFile(fileId) { file ->
            file.isOffline = isOffline
        }
    }

    private fun insertOrUpdateFile(
        realm: Realm,
        newFile: File,
        oldFile: File? = null,
        moreTransaction: ((realm: Realm) -> Unit)? = null
    ) {
        realm.executeTransaction {
            oldFile?.let { file ->
                newFile.isComplete = file.isComplete
                newFile.children = file.children
                newFile.responseAt = file.responseAt
                newFile.isOffline = file.isOffline
            }
            moreTransaction?.invoke(realm)
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

    fun saveFavoritesFiles(files: List<File>, replaceOldData: Boolean = false) {
        saveFiles(FAVORITES_FILE, files, replaceOldData) { oldFiles ->
            keepSubFolderChildren(oldFiles?.children, files)
        }
    }

    private fun saveMySharesFiles(files: ArrayList<File>, replaceOldData: Boolean) {
        val keepCaches = arrayListOf<Int>()
        val keepFiles = arrayListOf<Int>()
        Realm.getDefaultInstance()?.use { realm ->
            files.forEachIndexed { index, file ->
                val cacheFile = file.localPath(Realm.getApplicationContext()!!, File.LocalType.OFFLINE)
                val lastModified = cacheFile.lastModified() / 1000

                realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()?.let { oldFile ->
                    realm.executeTransaction {
                        file.children = oldFile.children
                        keepFiles.add(file.id)
                    }
                }

                if (cacheFile.exists() && lastModified == file.lastModifiedAt) {
                    files[index].isOffline = true
                    keepCaches.add(file.id)
                } else cacheFile.delete()
            }

            if (replaceOldData) removeFileCascade(MY_SHARES_FILE_ID, keepCaches, keepFiles, realm)
            saveFiles(MY_SHARES_FILE, files, replaceOldData, realm)
        }
    }

    private fun saveFiles(
        folder: File,
        files: List<File>,
        replaceOldData: Boolean = false,
        realm: Realm? = null,
        onTransaction: ((oldFiles: File?) -> Unit)? = null
    ) {
        val block: (Realm) -> Unit = { currentRealm ->
            val mySharesFolder = currentRealm.where(File::class.java).equalTo(File::id.name, folder.id).findFirst()
            currentRealm.executeTransaction { realm ->
                onTransaction?.invoke(mySharesFolder)
                val newMySharesFolder = if (replaceOldData) realm.copyToRealmOrUpdate(folder)
                else mySharesFolder ?: realm.copyToRealmOrUpdate(folder)
                newMySharesFolder?.children?.addAll(files)
            }
        }

        realm?.let(block) ?: Realm.getDefaultInstance()?.use(block)
    }

    fun switchDriveDB(userDrive: UserDrive) {
        Realm.setDefaultConfiguration(getRealmConfiguration(getDriveFileName(userDrive)))
    }

    private fun getDriveFileName(userDrive: UserDrive): String {
        val realmDb = if (userDrive.sharedWithMe) REALM_DB_SHARES_WITH_ME else REALM_DB_FILE
        return realmDb.format(userDrive.userId, userDrive.driveId)
    }

    private fun getRealmInstance(userDrive: UserDrive?): Realm {
        return userDrive?.let {
            Realm.getInstance(getRealmConfiguration(getDriveFileName(userDrive)))
        } ?: Realm.getDefaultInstance()
    }

    private fun getRealmConfiguration(dbName: String): RealmConfiguration {
        return RealmConfiguration.Builder()
            .deleteRealmIfMigrationNeeded()
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

    fun getFilesFromCache(folderID: Int, userDrive: UserDrive? = null): ArrayList<File> {
        return getRealmInstance(userDrive).use { currentRealm ->
            currentRealm.where(File::class.java).equalTo(File::id.name, folderID).findFirst()?.let { folder ->
                ArrayList(currentRealm.copyFromRealm(folder.children, 0))
            }
        } ?: ArrayList()
    }

    fun getFileDetails(fileId: Int, userDrive: UserDrive): File? {
        return getRealmInstance(userDrive).use { realm ->
            val apiResponse = ApiRepository.getFileDetails(File(id = fileId, driveId = userDrive.driveId))
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { remoteFile ->
                    insertOrUpdateFile(realm, remoteFile, getFileById(fileId, userDrive))
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
        sortType: File.SortType,
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
        sortType: File.SortType,
        page: Int = 1,
        ignoreCloud: Boolean = false,
        transaction: (files: ArrayList<File>, isComplete: Boolean) -> Unit
    ) {
        if (ignoreCloud) {
            transaction(getFilesFromCache(MY_SHARES_FILE_ID, userDrive), true)
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
            } else if (page == 1) transaction(getFilesFromCache(MY_SHARES_FILE_ID, userDrive), true)
        }
    }

    suspend fun cloudStorageSearch(
        userDrive: UserDrive,
        query: String,
        onResponse: (files: ArrayList<File>) -> Unit,
        page: Int = 1
    ) {
        val order = File.SortType.NAME_AZ
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
        Realm.getDefaultInstance().use { realm ->
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

    fun getDriveSoloPictures(customRealm: Realm? = null): ArrayList<File> {
        val operation: (Realm) -> ArrayList<File> = { realm ->
            realm.where(File::class.java).equalTo(File::id.name, PICTURES_FILE_ID).findFirst()?.let { picturesFolder ->
                realm.copyFromRealm(picturesFolder.children, 0) as ArrayList<File>
            } ?: arrayListOf()
        }
        return customRealm?.let(operation) ?: Realm.getDefaultInstance().use(operation)
    }

    fun storeFileActivities(fileActivities: ArrayList<FileActivity>) {
        Realm.getDefaultInstance()?.use { realm ->
            fileActivities.forEach { fileActivity ->
                fileActivity.userId = fileActivity.user?.id
                fileActivity.file?.let { file ->
                    realm.where(File::class.java).equalTo(File::id.name, file.id).findFirst()
                }?.let { file ->
                    fileActivity.file?.collaborativeFolder = file.collaborativeFolder
                    fileActivity.file?.children = file.children
                    fileActivity.file?.isComplete = file.isComplete
                    fileActivity.file?.isOffline = file.isOffline
                }
                realm.executeTransaction { it.insertOrUpdate(fileActivity) }
            }
        }
    }

    fun storeDriveSoloPictures(pictures: ArrayList<File>, customRealm: Realm? = null) {
        val block: (Realm) -> Unit = {
            it.executeTransaction { realm ->
                val picturesFolder = realm.where(File::class.java).equalTo(File::id.name, PICTURES_FILE_ID).findFirst()
                    ?: realm.copyToRealm(PICTURES_FILE)
                picturesFolder.children.addAll(pictures)
            }
        }
        customRealm?.let(block) ?: Realm.getDefaultInstance().use(block)
    }

    fun removeFileActivities() {
        Realm.getDefaultInstance()?.use { realm ->
            realm.executeTransaction {
                realm.where(FileActivity::class.java).findAll().deleteAllFromRealm()
                realm.where(File::class.java)
                    .greaterThan(File::id.name, Utils.ROOT_ID)
                    .isEmpty(File::localParent.name)
                    .findAll().deleteAllFromRealm()
            }
        }
    }

    fun getFilesFromCacheOrDownload(
        parentId: Int,
        page: Int,
        ignoreCache: Boolean = false,
        ignoreCloud: Boolean = false,
        order: File.SortType = File.SortType.NAME_AZ,
        userDrive: UserDrive?,
        customRealm: Realm? = null
    ): Pair<File, ArrayList<File>>? {
        val operation: (Realm) -> Pair<File, ArrayList<File>>? = { realm ->
            var result: Pair<File, ArrayList<File>>? = null
            val localFolder = getFileById(realm, parentId)
            val localFolderWithoutChildren = localFolder?.let { realm.copyFromRealm(it, 1) }
            val isNewSort = localFolder?.order != order.order || localFolder.orderBy != order.orderBy

            if (isNewSort && localFolder != null) {
                realm.executeTransaction {
                    localFolder.order = order.order
                    localFolder.orderBy = order.orderBy
                }
            }

            if ((ignoreCache || localFolder == null || localFolder.children.isNullOrEmpty() || !localFolder.isComplete) && !ignoreCloud) {
                result = downloadFiles(userDrive, parentId, page, order, localFolder, realm, localFolderWithoutChildren)
            } else if (page == 1 && localFolderWithoutChildren != null) {
                result = (localFolderWithoutChildren to getLocalSortedFolderFiles(localFolder, order, realm))
            }
            result
        }
        return customRealm?.let(operation) ?: getRealmInstance(userDrive).use(operation)
    }

    fun getFilesFromIdList(idList: Array<Int>, order: File.SortType = File.SortType.NAME_AZ): ArrayList<File> {
        return Realm.getDefaultInstance().use { realm ->
            realm
                .where(File::class.java)
                .`in`(File::id.name, idList)
                .getSortQueryByOrder(order)
                .findAll()?.let { files ->
                    realm.copyFromRealm(files) as ArrayList<File>
                } ?: arrayListOf()
        }
    }

    private fun downloadFiles(
        userDrive: UserDrive?,
        parentId: Int,
        page: Int,
        order: File.SortType,
        localFolder: File?,
        currentRealm: Realm,
        localFolderWithoutChildren: File?
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

                if (apiChildren.isNullOrEmpty()) {
                    localFolder?.let { oldFile ->
                        currentRealm.executeTransaction {
                            oldFile.isComplete = true
                            it.insertOrUpdate(oldFile)
                        }
                    }
                    if (localFolderWithoutChildren != null) result = (localFolderWithoutChildren to arrayListOf())
                } else {
                    saveRemoteFiles(localFolder, remoteFolder, page, currentRealm, apiChildren, order, apiResponse)
                    remoteFolder.children = RealmList()
                    result = (remoteFolder to apiChildren)
                }
            }
        } else if (page == 1 && localFolderWithoutChildren != null) {
            result = (localFolderWithoutChildren to getLocalSortedFolderFiles(localFolder, order, currentRealm))
        }
        return result
    }

    private fun saveRemoteFiles(
        localFolder: File?,
        remoteFolder: @RawValue File,
        page: Int,
        currentRealm: Realm,
        apiChildren: ArrayList<File>,
        order: File.SortType,
        apiResponse: ApiResponse<File>
    ) {
        // Restore same children data
        keepSubFolderChildren(localFolder?.children, remoteFolder.children)
        // Save to realm
        if (localFolder?.children.isNullOrEmpty() || page == 1) {
            saveRemoteFolder(currentRealm, remoteFolder, apiChildren.size, order, apiResponse.responseAt)
        } else {
            localFolder?.let { it ->
                addChildren(currentRealm, it, apiChildren)
                saveRemoteFolder(currentRealm, it, apiChildren.size, order, apiResponse.responseAt)
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
        order: File.SortType,
        responseAt: Long
    ) {
        insertOrUpdateFile(currentRealm, remoteFolder) {
            if (childrenSize < ApiRepository.PER_PAGE) remoteFolder.isComplete = true
            remoteFolder.order = order.order
            remoteFolder.orderBy = order.orderBy
            remoteFolder.responseAt = responseAt
        }
    }

    private fun getLocalSortedFolderFiles(
        localFolder: File?,
        order: File.SortType,
        currentRealm: Realm,
        localChildren: RealmResults<File>? = null
    ): ArrayList<File> {
        val children = localChildren ?: localFolder?.children
        return children?.where()?.let { realmQuery ->
            val results = realmQuery.getSortQueryByOrder(order).findAll()
            val isTeamSpaceName = File.VisibilityType.IS_TEAM_SPACE.name.lowercase(Locale.ROOT)
            val isSharedSpaceName = File.VisibilityType.IS_SHARED_SPACE.name.lowercase(Locale.ROOT)
            val teamSpaces = results.where().equalTo(File::visibility.name, isTeamSpaceName).findAll()
            val sharedSpaces = results.where().equalTo(File::visibility.name, isSharedSpaceName).findAll()
            val dirs = results.where().equalTo(File::type.name, File.Type.FOLDER.value)
                .notEqualTo(File::visibility.name, isTeamSpaceName)
                .and()
                .notEqualTo(File::visibility.name, isSharedSpaceName)
                .findAll()
            val files = results.where().equalTo(File::type.name, File.Type.FILE.value).findAll()

            arrayListOf<File>().apply {
                addAll(currentRealm.copyFromRealm(teamSpaces, 1))
                addAll(currentRealm.copyFromRealm(sharedSpaces, 1))
                addAll(currentRealm.copyFromRealm(dirs, 1))
                addAll(currentRealm.copyFromRealm(files, 1))
            }
        } ?: arrayListOf()
    }

    fun getFolderActivities(folder: File, page: Int, userDrive: UserDrive? = null): Map<out Int, File.LocalFileActivity> {
        return getRealmInstance(userDrive).use { realm ->
            getFolderActivitiesRec(realm, folder, page, userDrive)
        }
    }

    fun getFolderActivitiesRec(
        realm: Realm,
        folder: File,
        page: Int,
        userDrive: UserDrive? = null
    ): Map<out Int, File.LocalFileActivity> {
        val okHttpClient = runBlocking {
            userDrive?.userId?.let { KDriveHttpClient.getHttpClient(it) } ?: HttpClient.okHttpClient
        }
        val returnResponse = arrayMapOf<Int, File.LocalFileActivity>()
        val apiResponse = ApiRepository.getFileActivities(okHttpClient, folder, page)
        if (!apiResponse.isSuccess()) return returnResponse
        return if (apiResponse.data?.isNotEmpty() == true) {
            apiResponse.data?.forEach { fileActivity ->
                fileActivity.applyFileActivity(realm, returnResponse, folder)
            }

            if ((apiResponse.data?.size ?: 0) < ApiRepository.PER_PAGE) {
                updateFile(folder.id, realm) { file -> file.responseAt = apiResponse.responseAt }
                returnResponse
            } else returnResponse.apply { putAll(getFolderActivitiesRec(realm, folder, page + 1, userDrive)) }
        } else {
            updateFile(folder.id, realm) { file -> file.responseAt = apiResponse.responseAt }
            returnResponse
        }
    }

    private fun FileActivity.applyFileActivity(
        realm: Realm,
        returnResponse: ArrayMap<Int, File.LocalFileActivity>,
        folder: File
    ) {
        val fileId = this.fileId

        when (this.getAction()) {
            FileActivity.FileActivityType.FILE_DELETE,
            FileActivity.FileActivityType.FILE_MOVE_OUT,
            FileActivity.FileActivityType.FILE_TRASH -> {
                if (returnResponse[this.fileId] == null) {
                    getFileById(fileId)?.let { localFile ->
                        val localFolder = localFile.localParent?.find { it.id == folder.id }
                        val existsInFolder = localFolder != null
                        if (existsInFolder) {
                            removeFileCascade(fileId, realm = realm)
                        }
                    }
                    returnResponse[this.fileId] = File.LocalFileActivity.IS_DELETE
                }
            }
            FileActivity.FileActivityType.FILE_MOVE_IN,
            FileActivity.FileActivityType.FILE_RESTORE,
            FileActivity.FileActivityType.FILE_CREATE -> {
                if (returnResponse[this.fileId] == null) {
                    realm.where(File::class.java).equalTo(File::id.name, folder.id).findFirst()?.let { currentFolder ->
                        if (this.file != null && !currentFolder.children.contains(this.file)) {
                            addChild(realm, currentFolder, this.file!!)
                            returnResponse[this.fileId] = File.LocalFileActivity.IS_NEW
                        }
                    }
                }
            }
            FileActivity.FileActivityType.FILE_UPDATE,
            FileActivity.FileActivityType.FILE_RENAME,
            FileActivity.FileActivityType.FILE_FAVORITE_CREATE,
            FileActivity.FileActivityType.FILE_FAVORITE_REMOVE,
            FileActivity.FileActivityType.FILE_SHARE_CREATE,
            FileActivity.FileActivityType.FILE_SHARE_UPDATE,
            FileActivity.FileActivityType.FILE_SHARE_DELETE -> {
                if (returnResponse[this.fileId] == null) {
                    updateFileFromActivity(realm, returnResponse, this, folder.id)
                }
            }
            else -> Unit
        }
    }

    fun getOfflineFiles(order: File.SortType?, userDrive: UserDrive = UserDrive(), customRealm: Realm? = null): ArrayList<File> {
        val block: (Realm) -> ArrayList<File> = { realm ->
            realm.where(File::class.java).equalTo(File::isOffline.name, true).findAll()?.let { files ->
                if (order == null) realm.copyFromRealm(files) as ArrayList
                else getLocalSortedFolderFiles(null, order, realm, files)
            } ?: arrayListOf()
        }
        return customRealm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    fun searchFiles(
        query: String,
        order: File.SortType,
        userDrive: UserDrive = UserDrive(),
        customRealm: Realm? = null
    ): ArrayList<File> {
        val block: (Realm) -> ArrayList<File> = { currRealm ->
            currRealm.where(File::class.java).like(File::name.name, "*$query*").findAll()?.let { files ->
                getLocalSortedFolderFiles(null, order, currRealm, files)
            } ?: arrayListOf()
        }
        return customRealm?.let(block) ?: getRealmInstance(userDrive).use(block)
    }

    private fun updateFileFromActivity(
        realm: Realm,
        returnResponse: ArrayMap<Int, File.LocalFileActivity>,
        fileActivity: FileActivity,
        folderId: Int
    ) {
        returnResponse[fileActivity.fileId] = File.LocalFileActivity.IS_UPDATE
        if (fileActivity.getAction() == FileActivity.FileActivityType.FILE_UPDATE) {
            getFileById(realm, fileActivity.fileId)?.deleteCaches(Realm.getApplicationContext()!!)
        }

        realm.where(File::class.java).equalTo(File::id.name, fileActivity.fileId).findFirst()?.let { file ->
            insertOrUpdateFile(realm, fileActivity.file!!, file)
        } ?: also {
            returnResponse[fileActivity.fileId] = File.LocalFileActivity.IS_NEW
            realm.executeTransaction {
                realm.where(File::class.java).equalTo(File::id.name, folderId).findFirst()?.children?.add(fileActivity.file)
            }
        }
    }

    fun addFileTo(parentFolderID: Int, file: File, userDrive: UserDrive?) {
        getRealmInstance(userDrive).use { realm ->
            val localFolder = realm.where(File::class.java).equalTo(File::id.name, parentFolderID).findFirst()
            if (localFolder != null) {
                realm.executeTransaction {
                    localFolder.children.add(file)
                }
            }
        }
    }

    fun Context.startDownloadFile(downloadURL: Uri, fileName: String) {
        val request = DownloadManager.Request(downloadURL).apply {
            setTitle(fileName)
            setDescription(getString(R.string.app_name))
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            HttpUtils.getHeaders(contentType = null).toMap().forEach { addRequestHeader(it.key, it.value) }
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) setVisibleInDownloadsUi(true)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    private fun RealmQuery<File>.getSortQueryByOrder(order: File.SortType): RealmQuery<File> {
        return when (order) {
            File.SortType.NAME_AZ -> this.sort(File::nameNaturalSorting.name, Sort.ASCENDING)
            File.SortType.NAME_ZA -> this.sort(File::nameNaturalSorting.name, Sort.DESCENDING)
            File.SortType.OLDER -> this.sort(File::lastModifiedAt.name, Sort.ASCENDING)
            File.SortType.RECENT -> this.sort(File::lastModifiedAt.name, Sort.DESCENDING)
            File.SortType.OLDER_TRASHED -> this.sort(File::deletedAt.name, Sort.ASCENDING)
            File.SortType.RECENT_TRASHED -> this.sort(File::deletedAt.name, Sort.DESCENDING)
            File.SortType.SMALLER -> this.sort(File::size.name, Sort.ASCENDING)
            File.SortType.BIGGER -> this.sort(File::size.name, Sort.DESCENDING)
            File.SortType.EXTENSION -> this.sort(File::convertedType.name, Sort.ASCENDING) // TODO implement
        }
    }
}