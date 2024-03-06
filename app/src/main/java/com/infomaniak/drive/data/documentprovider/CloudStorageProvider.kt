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
package com.infomaniak.drive.data.documentprovider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DownloadOfflineFileManager
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.cancelNotification
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import com.infomaniak.lib.core.utils.SentryLog
import io.realm.Realm
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.URLEncoder
import java.util.Date
import java.util.UUID

class CloudStorageProvider : DocumentsProvider() {

    private lateinit var cacheDir: java.io.File

    override fun onCreate(): Boolean {
        SentryLog.d(TAG, "onCreate")

        var result = false
        runBlocking {
            context?.let {
                cacheDir = java.io.File(it.filesDir, "cloud_storage_temp_files")
                it.initRealm()
                result = true
            }
        }
        return result
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        SentryLog.d(TAG, "queryRoots")
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        AccountUtils.getAllUsersSync().forEach { user ->
            cursor.addRoot(user.id.toString(), user.id.toString(), user.email)
            CoroutineScope(Dispatchers.IO).launch {
                context?.let {
                    val okHttpClient = AccountUtils.getHttpClient(user.id)
                    AccountUtils.updateCurrentUserAndDrives(it, fromCloudStorage = true, okHttpClient = okHttpClient)
                }
            }
        }

        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        SentryLog.d(TAG, "queryDocument(), documentId=$documentId")

        return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            val userId = getUserId(documentId)
            val driveDocument = getDriveFromDocId(documentId)
            val isDriveFolder = getFileIdFromDocumentId(documentId) == 1
            val isRootFolder = documentId == userId
            val isSharedWithMeFolder = getFileIdFromDocumentId(documentId) == SHARED_WITHME_FOLDER_ID
            val isMySharesFolder = getFileIdFromDocumentId(documentId) == MY_SHARES_FOLDER_ID
            val fromSharedWithMe = comeFromSharedWithMe(documentId)

            when {
                isRootFolder || isDriveFolder -> addFile(null, documentId, driveDocument.name)
                isSharedWithMeFolder -> addFile(
                    null,
                    documentId,
                    context?.getString(R.string.sharedWithMeTitle) ?: "Shared with me"
                )
                isMySharesFolder -> addFile(
                    null,
                    documentId,
                    context?.getString(R.string.mySharesTitle) ?: "My Shares"
                )
                else -> { // isFile
                    val fileId = getFileIdFromDocumentId(documentId)
                    val userDrive = UserDrive(userId.toInt(), driveDocument.id, fromSharedWithMe)
                    FileController.getRealmInstance(userDrive).use { realm ->
                        FileController.getFileProxyById(fileId, customRealm = realm)?.let { file ->
                            addFile(file, documentId)
                        }
                    }
                }
            }
        }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        SentryLog.d(TAG, "queryChildDocuments(), parentDocumentId=$parentDocumentId, sort=$sortOrder")

        val cursor = DocumentCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        val uri = DocumentCursor.createUri(context, parentDocumentId)
        val isNewJob = uri != oldQueryChildUri || needRefresh
        val isLoading = uri == oldQueryChildUri && oldQueryChildCursor?.job?.isCompleted == false || isNewJob

        SentryLog.i(TAG, "queryChildDocuments(), isLoading=$isLoading, isNew=$isNewJob")

        cursor.extras = bundleOf(DocumentsContract.EXTRA_LOADING to isLoading)
        cursor.setNotificationUri(context?.contentResolver, uri)

        if (isNewJob) {
            oldQueryChildCursor?.close()
            oldQueryChildUri = uri
            oldQueryChildCursor = cursor
            currentParentDocumentId = parentDocumentId
            needRefresh = false
        } else {
            cursor.restore(oldQueryChildCursor!!)
            cursor.extras = bundleOf(DocumentsContract.EXTRA_LOADING to false)
            cursor.setNotificationUri(context?.contentResolver, uri)
            return cursor
        }

        val fileFolderId = getFileIdFromDocumentId(parentDocumentId)
        val isRootFolder = parentDocumentId == getUserId(parentDocumentId)
        val isSharedWithMeFolder = fileFolderId == SHARED_WITHME_FOLDER_ID
        val isMySharesFolder = fileFolderId == MY_SHARES_FOLDER_ID

        val userId = getUserId(parentDocumentId).toInt()
        val driveId = getDriveFromDocId(parentDocumentId).id
        val sortType = getSortType(sortOrder)

        when {
            isRootFolder -> {
                cursor.addRootDrives(userId, isRootFolder = true)

                var documentId = parentDocumentId + SEPARATOR + MY_SHARES_FOLDER_ID
                var name = context?.getString(R.string.mySharesTitle) ?: "My Shares"
                cursor.addFile(null, documentId, name)

                if (DriveInfosController.getDrivesCount(userId = userId, sharedWithMe = true).isPositive()) {
                    documentId = parentDocumentId + SEPARATOR + SHARED_WITHME_FOLDER_ID
                    name = context?.getString(R.string.sharedWithMeTitle) ?: "Shared with me"
                    cursor.addFile(null, documentId, name)
                }
            }
            isSharedWithMeFolder -> cursor.addRootDrives(userId, SHARED_WITHME_FOLDER_ID, true)
            isMySharesFolder -> cursor.addRootDrives(userId, MY_SHARES_FOLDER_ID)
            isSharedUri(parentDocumentId) && fileFolderId == Utils.ROOT_ID -> {
                CoroutineScope(Dispatchers.IO + cursor.job).launch {
                    if (parentDocumentId.contains(MY_SHARES_FOLDER_ID.toString())) {
                        FileController.getMySharedFiles(
                            UserDrive(userId, driveId),
                            sortType
                        ) { files, _ -> cursor.addFiles(parentDocumentId, uri)(files) }
                    } else {
                        FileController.getCloudStorageFiles(
                            parentId = fileFolderId,
                            userDrive = UserDrive(userId, driveId, sharedWithMe = true),
                            sortType = sortType,
                            transaction = cursor.addFiles(parentDocumentId, uri)
                        )
                    }
                }
            }
            else -> {
                CoroutineScope(Dispatchers.IO + cursor.job).launch {
                    FileController.getCloudStorageFiles(
                        parentId = fileFolderId,
                        userDrive = UserDrive(userId, driveId),
                        sortType = sortType,
                        transaction = cursor.addFiles(parentDocumentId, uri)
                    )
                }
            }
        }

        cursor.extras = bundleOf(DocumentsContract.EXTRA_LOADING to false)
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    private fun DocumentCursor.addFiles(parentDocumentId: String, uri: Uri): (files: ArrayList<File>) -> Unit = { files ->
        files.forEach { file -> this.addFile(file, createFileDocumentId(parentDocumentId, file.id)) }
        context?.contentResolver?.notifyChange(uri, null)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        SentryLog.d(TAG, "openDocument(), id=$documentId, mode=$mode, signalIsCancelled: ${signal?.isCanceled}")
        val context = context ?: return null

        val isWrite = mode.indexOf('w') != -1
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val fileId = getFileIdFromDocumentId(documentId)
        val userDrive = createUserDrive(documentId)
        val file = FileController.getFileById(fileId, userDrive)

        return file?.let {
            if (isWrite) {
                writeDataFile(context, file, userDrive, accessMode)
            } else {
                getDataFile(context, file, userDrive, accessMode)
            }
        }
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        SentryLog.d(TAG, "querySearchDocuments(), rootId=$rootId, query=$query, projection=$projection, $currentParentDocumentId")

        val cursor = DocumentCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        if (currentParentDocumentId == null ||
            currentParentDocumentId == getUserId(currentParentDocumentId!!) ||
            getFileIdFromDocumentId(currentParentDocumentId!!) == SHARED_WITHME_FOLDER_ID ||
            getFileIdFromDocumentId(currentParentDocumentId!!) == MY_SHARES_FOLDER_ID
        ) {
            cursor.extras =
                bundleOf(DocumentsContract.EXTRA_ERROR to context?.getString(R.string.cloudStorageQuerySearchImpossibleSelectDrive))
            return cursor
        }

        val parentDocumentId = currentParentDocumentId!!

        val uri = DocumentCursor.createUri(context, "$rootId/search/$query")
        val isNewSearch = uri != oldSearchUri
        val isLoading = uri == oldSearchUri && oldSearchCursor?.job?.isCompleted == false || isNewSearch

        cursor.extras = bundleOf(DocumentsContract.EXTRA_LOADING to isLoading)
        cursor.setNotificationUri(context?.contentResolver, uri)

        if (isNewSearch) {
            oldSearchCursor?.close()
            oldSearchUri = uri
            oldSearchCursor = cursor
        } else {
            cursor.restore(oldSearchCursor!!)
            return cursor
        }

        val driveId = getDriveFromDocId(parentDocumentId).id
        val userId = getUserId(parentDocumentId)
        val userDrive = UserDrive(userId.toInt(), driveId, comeFromSharedWithMe(parentDocumentId))

        CoroutineScope(Dispatchers.IO + cursor.job).launch {
            FileController.cloudStorageSearch(userDrive, query, onResponse = { files ->
                files.forEach { file -> cursor.addFile(file, createFileDocumentId(parentDocumentId, file.id)) }
                if (files.isNotEmpty()) context?.contentResolver?.notifyChange(uri, null)
            })
        }

        return cursor
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
        SentryLog.d(TAG, "openDocumentThumbnail(), id=$documentId, signalIsCancelled: ${signal?.isCanceled}")

        val fileId = getFileIdFromDocumentId(documentId)
        val userId = getUserId(documentId)

        return FileController.getRealmInstance(createUserDrive(documentId)).use { realm ->

            FileController.getFileProxyById(fileId, customRealm = realm)?.let { file ->

                generateThumbnail(fileId, file, userId)?.let { parcel ->
                    AssetFileDescriptor(parcel, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                }

            } ?: super.openDocumentThumbnail(documentId, sizeHint, signal)
        }
    }

    private fun generateThumbnail(fileId: Int, file: File, userId: String): ParcelFileDescriptor? {

        val outputFolder = java.io.File(context?.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val name = "${fileId}_${file.name}"
        val outputFile = java.io.File(outputFolder, name)
        var parcel: ParcelFileDescriptor? = null

        try {
            val okHttpClient = runBlocking { AccountUtils.getHttpClient(userId.toInt()) }
            val response = DownloadOfflineFileManager.downloadFileResponse(file.thumbnail(), okHttpClient)

            if (response.isSuccessful) {
                DownloadOfflineFileManager.saveRemoteData(TAG, response, outputFile) {
                    parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } else if (outputFile.exists()) {
                parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

        } catch (exception: Exception) {
            exception.printStackTrace()

            if (outputFile.exists()) {
                parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }

        return parcel
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        SentryLog.d(TAG, "createDocument(), parentId=$parentDocumentId, mimeType=$mimeType, name=$displayName")

        return if (mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR, true)) {
            createNewFolder(parentDocumentId, displayName) // If we want to create a new folder
        } else {
            createNewFile(parentDocumentId, displayName) // If another provider copies or moves a file into kDrive
        }
    }

    override fun deleteDocument(documentId: String) {
        SentryLog.d(TAG, "deleteDocument(), id=$documentId")

        val context = context ?: throw IllegalStateException("Delete document failed: missing Android Context")

        FileController.getRealmInstance(createUserDrive(documentId)).use { realm ->
            FileController.getFileProxyById(getFileIdFromDocumentId(documentId), customRealm = realm)?.let { file ->

                // Delete
                val apiResponse = FileController.deleteFile(file, realm, context = context)

                if (apiResponse.isSuccess()) {

                    // Delete orphans
                    FileController.removeOrphanAndActivityFiles(realm)

                    // Refresh
                    scheduleRefresh()

                } else {
                    throw RuntimeException("Delete document failed")
                }
            }
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        SentryLog.d(TAG, "renameDocument(), id=$documentId, name=$displayName")

        FileController.getRealmInstance(createUserDrive(documentId)).use { realm ->
            FileController.getFileProxyById(getFileIdFromDocumentId(documentId), customRealm = realm)?.let { file ->

                // Rename
                val apiResponse = FileController.renameFile(file, displayName, realm)

                if (apiResponse.isSuccess()) {
                    // Refresh
                    scheduleRefresh()

                } else {
                    throw RuntimeException("Rename document failed")
                }
            }
        }

        return null
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        SentryLog.d(TAG, "copyDocument(), sourceId=$sourceDocumentId, targetParentId=$targetParentDocumentId")

        return FileController.getRealmInstance(createUserDrive(sourceDocumentId)).use { realm ->

            val fileId = getFileIdFromDocumentId(sourceDocumentId)
            val file =
                FileController.getFileProxyById(fileId, customRealm = realm) ?: throw IllegalStateException("File not found")
            val copyName = file.name
            val targetParentFileId = getFileIdFromDocumentId(targetParentDocumentId)

            val apiResponse = ApiRepository.copyFile(file, copyName, targetParentFileId)

            if (apiResponse.isSuccess()) {

                // Refresh
                scheduleRefresh()

                return@use sourceDocumentId
            } else {
                throw RuntimeException("Copy document failed")
            }
        }
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        SentryLog.d(
            TAG, "moveDocument(), " +
                    "sourceId=$sourceDocumentId, " +
                    "sourceParentId=$sourceParentDocumentId, " +
                    "targetParentId=$targetParentDocumentId"
        )

        return FileController.getRealmInstance(createUserDrive(sourceDocumentId)).use { realm ->

            val fileNotFoundException = IllegalStateException("File not found")

            val fileId = getFileIdFromDocumentId(sourceDocumentId)
            val file = FileController.getFileProxyById(fileId, customRealm = realm) ?: throw fileNotFoundException

            val targetParentFileId = getFileIdFromDocumentId(targetParentDocumentId)
            val targetParentFile =
                FileController.getFileProxyById(targetParentFileId, customRealm = realm) ?: throw fileNotFoundException

            val apiResponse = ApiRepository.moveFile(file, targetParentFile)

            if (apiResponse.isSuccess()) {
                realm.executeTransaction { file.deleteFromRealm() }

                // Refresh
                scheduleRefresh(sourceParentDocumentId)

                return@use sourceDocumentId

            } else {
                throw RuntimeException("Move document failed")
            }
        }
    }

    private fun createNewFolder(parentDocumentId: String, displayName: String): String {

        val userDrive = createUserDrive(parentDocumentId)
        val parentId = getFileIdFromDocumentId(parentDocumentId)

        val apiResponse = runBlocking { FileController.createFolder(displayName, parentId, true, userDrive) }
        val file = apiResponse.data

        if (apiResponse.isSuccess() && file != null) {

            FileController.addFileTo(parentId, file, userDrive)

            // Refresh
            scheduleRefresh()

            return createFileDocumentId(parentDocumentId, file.id)
        }

        throw RuntimeException("Create folder failed")
    }

    private fun createNewFile(parentDocumentId: String, displayName: String): String {

        // If we don't have the permissions, notify the user
        showSyncPermissionNotification()

        val driveId = getDriveFromDocId(parentDocumentId).id
        val parentFolderId = getFileIdFromDocumentId(parentDocumentId)
        val userDrive = createUserDrive(parentDocumentId)

        val uploadUrl = ApiRoutes.uploadFile(driveId) +
                "?directory_id=$parentFolderId" +
                "&total_size=0" +
                "&file_name=${URLEncoder.encode(displayName, "UTF-8")}" +
                "&last_modified_at=${Date().time / 1_000L}" +
                "&conflict=" + UploadTask.Companion.ConflictOption.RENAME.toString()

        val apiResponse = ApiController.callApi<ApiResponse<File>>(
            uploadUrl,
            ApiController.ApiMethod.POST,
            okHttpClient = runBlocking { AccountUtils.getHttpClient(userDrive.userId, 120) }
        )
        val file = apiResponse.data

        if (apiResponse.isSuccess() && file != null) {
            FileController.addFileTo(parentFolderId, file, userDrive)

            createTempFile(getFileIdFromDocumentId(parentDocumentId), displayName)

            return createFileDocumentId(parentDocumentId, file.id)
        }

        throw RuntimeException("Copy file failed")
    }

    private fun createTempFile(parentFileId: Int, displayName: String): java.io.File {
        val tempFileFolder = java.io.File(cacheDir, "$parentFileId").apply { if (!exists()) mkdirs() }
        return java.io.File(tempFileFolder, displayName).apply { if (!exists()) createNewFile() }
    }

    private fun writeDataFile(context: Context, file: File, userDrive: UserDrive, accessMode: Int): ParcelFileDescriptor? {
        val tempFile = createTempFile(file.parentId, file.name)
        val handler = Handler(context.mainLooper)

        return ParcelFileDescriptor.open(tempFile, accessMode, handler) { exception: IOException? ->
            if (exception == null) {
                CoroutineScope(Dispatchers.IO).launch {
                    UploadFile(
                        uri = tempFile.toUri().toString(),
                        driveId = userDrive.driveId,
                        fileCreatedAt = Date(),
                        fileName = file.name,
                        fileSize = tempFile.length(),
                        remoteFolder = file.parentId,
                        type = UploadFile.Type.CLOUD_STORAGE.name,
                        userId = userDrive.userId,
                    ).store()
                    context.syncImmediately()
                }
            } else {
                exception.printStackTrace()
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.INFO
                    scope.setExtra("tempFile path", tempFile.path)
                    Sentry.captureException(exception)
                }
            }
        }
    }

    private fun getDataFile(context: Context, file: File, userDrive: UserDrive, accessMode: Int): ParcelFileDescriptor? {
        val cacheFile = file.getCacheFile(context, userDrive)
        val offlineFile = file.getOfflineFile(context, userDrive.userId)

        try {
            // Get offline file if it's intact
            if (offlineFile != null && file.isOfflineAndIntact(offlineFile)) {
                return ParcelFileDescriptor.open(offlineFile, accessMode)
            }

            // Get cache file if it's exists and if the file is updated
            if (!file.isObsolete(cacheFile) && file.size == cacheFile.length()) {
                return ParcelFileDescriptor.open(cacheFile, accessMode)
            }

            // Download data file
            val okHttpClient = runBlocking { AccountUtils.getHttpClient(userDrive.userId) }
            val response = DownloadOfflineFileManager.downloadFileResponse(
                fileUrl = ApiRoutes.downloadFile(file),
                okHttpClient = okHttpClient,
                downloadInterceptor = DownloadOfflineFileManager.downloadProgressInterceptor(onProgress = { progress ->
                    SentryLog.i(TAG, "open currentProgress: $progress")
                })
            )

            if (response.isSuccessful) {
                DownloadOfflineFileManager.saveRemoteData(TAG, response, cacheFile)
                cacheFile.setLastModified(file.getLastModifiedInMilliSecond())
                return ParcelFileDescriptor.open(cacheFile, accessMode)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

        return null
    }

    private fun scheduleRefresh(sourceParentDocumentId: String? = currentParentDocumentId) {
        sourceParentDocumentId?.let {
            needRefresh = true
            context?.contentResolver?.notifyChange(DocumentCursor.createUri(context, it), null)
        }
    }

    @SuppressLint("BatteryLife")
    private fun showSyncPermissionNotification() {
        context?.let { context ->

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
            ) {

                // Cancel previous notification
                context.cancelNotification(syncPermissionNotifId)

                // Display new notification
                context.buildGeneralNotification(context.getString(R.string.uploadPermissionError)).apply {
                    setContentText(context.getString(R.string.cloudStorageMissingPermissionNotifDescription))
                    setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            syncPermissionNotifId,
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ),
                            NotificationUtilsCore.pendingIntentFlags
                        )
                    )
                    NotificationManagerCompat.from(context).notify(syncPermissionNotifId, build())
                }
            }
        }
    }

    private fun MatrixCursor.addRoot(rootId: String, documentId: String, summary: String) {
        val flags = DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                DocumentsContract.Root.FLAG_SUPPORTS_SEARCH

        newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.app_name))
            add(DocumentsContract.Root.COLUMN_SUMMARY, summary)
            add(DocumentsContract.Root.COLUMN_FLAGS, flags)
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
    }

    private fun MatrixCursor.addRootDrives(
        userId: Int,
        shareFolderId: Int? = null,
        shareWithMe: Boolean = false,
        isRootFolder: Boolean = false
    ) {
        runBlocking {
            DriveInfosController.getDrives(userId, sharedWithMe = shareWithMe).forEach { drive ->
                val driveDocument = "${drive.name}$DRIVE_SEPARATOR${drive.id}"
                val documentId =
                    if (shareFolderId == null) "$userId$SEPARATOR$driveDocument$SEPARATOR${Utils.ROOT_ID}"
                    else "$userId$SEPARATOR$shareFolderId$SEPARATOR$driveDocument$SEPARATOR${Utils.ROOT_ID}"
                addFile(null, documentId, drive.name, isRootFolder)
            }
        }
    }

    private fun MatrixCursor.addFile(file: File?, documentId: String, name: String = "", isRootFolder: Boolean = false) {
        var flags = 0

        if (file?.hasCreationRight() == true || isRootFolder) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }

        val mimetype = if (file == null || file.isFolder()) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else file.getMimeType()

        if (file?.isFolder() == false && file.hasThumbnail) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        }

        if (file != null) {
            flags = flags or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = flags or
                        DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE
            }
        }

        newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimetype)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file?.name ?: name)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file?.lastModifiedAt?.times(1000))
            add(DocumentsContract.Document.COLUMN_SIZE, file?.size)
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }
    }

    private fun MatrixCursor.restore(cursor: MatrixCursor) {
        var position = 0
        while (cursor.moveToPosition(position)) {
            newRow().apply {
                val docId = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val mimetype = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
                val displayName = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                val size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE))
                val flags = cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimetype)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
                add(DocumentsContract.Document.COLUMN_SIZE, size)
                add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            }
            position++
        }
    }

    private data class DriveDocument(val name: String, val id: Int)

    companion object {

        private val mutex = Mutex()

        private const val TAG = "CloudStorageProvider"
        private const val SEPARATOR = "/"
        private const val DRIVE_SEPARATOR = "@"
        private const val MY_SHARES_FOLDER_ID = -1
        private const val SHARED_WITHME_FOLDER_ID = -2

        private val SHARED_URI_REGEX = Regex("\\d+/-\\d+/.+$DRIVE_SEPARATOR\\d+/\\d+")

        private var currentParentDocumentId: String? = null

        private var oldQueryChildUri: Uri? = null
        private var oldQueryChildCursor: DocumentCursor? = null

        private var oldSearchUri: Uri? = null
        private var oldSearchCursor: DocumentCursor? = null

        private var needRefresh: Boolean = false

        private val syncPermissionNotifId = UUID.randomUUID().hashCode()

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        suspend fun Context.initRealm() {
            mutex.withLock {
                try {
                    Realm.getDefaultInstance()
                } catch (exception: Exception) {
                    Realm.init(this)
                    AccountUtils.init(this)
                }
            }
        }

        private fun getUserId(documentId: String) = documentId.substringBefore(SEPARATOR)

        private fun getFileIdFromDocumentId(documentId: String) = documentId.substringAfterLast(SEPARATOR).toInt()

        private fun createFileDocumentId(parentDocumentId: String, fileId: Int): String {
            return parentDocumentId.substringBeforeLast(SEPARATOR) + SEPARATOR + fileId.toString()
        }

        private fun isSharedUri(documentId: String) = documentId.matches(SHARED_URI_REGEX)

        private fun getDriveFromDocId(documentId: String): DriveDocument {
            val parentDocumentId = documentId.substringBeforeLast(SEPARATOR)
            return if (documentId.contains(DRIVE_SEPARATOR)) {
                val drive = parentDocumentId.substringAfter(SEPARATOR).split(DRIVE_SEPARATOR)
                DriveDocument(name = drive[0], id = drive[1].toInt())
            } else DriveDocument(name = "", id = -1)
        }

        private fun getSortType(sortOrder: String?): File.SortType {
            return when (sortOrder) {
                "_display_name ASC" -> File.SortType.NAME_AZ
                "_display_name DESC" -> File.SortType.NAME_ZA
                "last_modified ASC" -> File.SortType.OLDER
                "last_modified DESC" -> File.SortType.RECENT
                "_size ASC" -> File.SortType.SMALLER
                "_size DESC" -> File.SortType.BIGGER
                else -> File.SortType.NAME_AZ
            }
        }

        fun createShareFileUri(context: Context, file: File, userDrive: UserDrive = UserDrive()): Uri? {
            val currentUserId = userDrive.userId
            val currentDriveId = userDrive.driveId

            DriveInfosController.getDrive(currentUserId, currentDriveId)?.let { drive ->
                val baseContentUri = "content://${context.getString(R.string.CLOUD_STORAGE_AUTHORITY)}/document/"
                val sharedId = if (drive.sharedWithMe) "${SHARED_WITHME_FOLDER_ID}$SEPARATOR" else ""
                val content = "$currentUserId$SEPARATOR$sharedId${drive.name}$DRIVE_SEPARATOR$currentDriveId/${file.id}"
                return "$baseContentUri${URLEncoder.encode(content, "UTF-8")}".toUri()
            }

            return null
        }

        private fun comeFromSharedWithMe(documentId: String): Boolean =
            documentId.split("/").getOrNull(1) == SHARED_WITHME_FOLDER_ID.toString()

        private fun createUserDrive(documentId: String): UserDrive =
            UserDrive(
                getUserId(documentId).toInt(),
                getDriveFromDocId(documentId).id,
                comeFromSharedWithMe(documentId)
            )

        fun notifyRootsChanged(context: Context) {
            val authority = context.getString(R.string.CLOUD_STORAGE_AUTHORITY)
            val rootsUri = DocumentsContract.buildRootsUri(authority)
            context.contentResolver.notifyChange(rootsUri, null)
        }
    }
}
