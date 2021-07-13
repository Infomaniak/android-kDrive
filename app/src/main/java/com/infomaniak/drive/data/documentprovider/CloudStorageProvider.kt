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
package com.infomaniak.drive.data.documentprovider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.Utils
import io.realm.Realm
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.net.URLEncoder

class CloudStorageProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate")
        var result = false
        runBlocking {
            mutex.withLock {
                context?.let {
                    try {
                        Realm.getDefaultInstance()
                    } catch (exception: Exception) {
                        Sentry.captureMessage("Realm.init in CloudStorageProvider")
                        Realm.init(it)
                        AccountUtils.init(it)
                    }
                    result = true
                }
            }
        }
        return result
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots")
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        AccountUtils.getAllUsersSync().forEach { user ->
            cursor.addRoot(user.id.toString(), user.id.toString(), user.email)
            GlobalScope.launch(Dispatchers.IO) {
                context?.let {
                    val okHttpClient = KDriveHttpClient.getHttpClient(user.id)
                    AccountUtils.updateCurrentUserAndDrives(it, fromCloudStorage = true, okHttpClient = okHttpClient)
                }
            }
        }

        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryDocument(), documentId=$documentId")

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
                    FileController.getFileById(fileId, userDrive)?.let { file ->
                        addFile(file, documentId)
                    }
                }
            }
        }
    }

    private fun comeFromSharedWithMe(documentId: String): Boolean {
        return documentId.split("/").getOrNull(1) == SHARED_WITHME_FOLDER_ID.toString()
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        Log.d(TAG, "queryChildDocuments(), parentDocumentId=$parentDocumentId, sort=$sortOrder")

        val cursor = DocumentCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        val uri = DocumentCursor.createUri(context, parentDocumentId)
        val isNewJob = uri != oldQueryChildUri
        val isLoading = uri == oldQueryChildUri && oldQueryChildCursor?.job?.isCompleted == false || isNewJob

        Log.i(TAG, "queryChildDocuments() isloading:$isLoading, isnew: $isNewJob")

        cursor.extras = bundleOf(DocumentsContract.EXTRA_LOADING to isLoading)
        cursor.setNotificationUri(context?.contentResolver, uri)

        if (isNewJob) {
            oldQueryChildCursor?.close()
            oldQueryChildUri = uri
            oldQueryChildCursor = cursor
            currentParentDocumentId = parentDocumentId
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
                cursor.addRootDrives(userId)

                var documentId = parentDocumentId + SEPARATOR + MY_SHARES_FOLDER_ID
                var name = context?.getString(R.string.mySharesTitle) ?: "My Shares"
                cursor.addFile(null, documentId, name)

                val sharesWithMe = DriveInfosController.getDrives(userId = userId, sharedWithMe = true).isNotEmpty()
                if (sharesWithMe) {
                    documentId = parentDocumentId + SEPARATOR + SHARED_WITHME_FOLDER_ID
                    name = context?.getString(R.string.sharedWithMeTitle) ?: "Shared with me"
                    cursor.addFile(null, documentId, name)
                }
            }
            isSharedWithMeFolder -> cursor.addRootDrives(userId, SHARED_WITHME_FOLDER_ID, true)
            isMySharesFolder -> cursor.addRootDrives(userId, MY_SHARES_FOLDER_ID)
            isSharedUri(parentDocumentId) && fileFolderId == Utils.ROOT_ID -> {
                GlobalScope.launch(Dispatchers.IO + cursor.job) {
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
                GlobalScope.launch(Dispatchers.IO + cursor.job) {
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
        Log.d(TAG, "openDocument(), id=$documentId, mode=$mode, signalIsCancelled: ${signal?.isCanceled}")
        val context = context ?: return null

        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val fileId = getFileIdFromDocumentId(documentId)
        val driveId = getDriveFromDocId(documentId).id
        val userId = getUserId(documentId).toInt()
        val userDrive = UserDrive(userId, driveId, comeFromSharedWithMe(documentId))
        val file = FileController.getFileById(fileId, userDrive)

        if (file != null) {
            return getDataFile(context, file, userDrive, accessMode)
        }
        return null
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        Log.d(TAG, "querySearchDocuments(), rootID=$rootId, query=$query, projection=$projection, $currentParentDocumentId")

        val cursor = DocumentCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        if (currentParentDocumentId == null ||
            currentParentDocumentId == getUserId(currentParentDocumentId!!) ||
            getFileIdFromDocumentId(currentParentDocumentId!!) == SHARED_WITHME_FOLDER_ID ||
            getFileIdFromDocumentId(currentParentDocumentId!!) == MY_SHARES_FOLDER_ID
        ) {
            cursor.extras =
                bundleOf(DocumentsContract.EXTRA_ERROR to context?.getString(R.string.cloudStorageSuerySearchImpossibleSelectDrive))
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

        GlobalScope.launch(Dispatchers.IO + cursor.job) {
            FileController.cloudStorageSearch(userDrive, query, onResponse = { files ->
                files.forEach { file -> cursor.addFile(file, createFileDocumentId(parentDocumentId, file.id)) }
                if (files.isNotEmpty()) context?.contentResolver?.notifyChange(uri, null)
            })
        }

        return cursor
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
        Log.d(TAG, "openDocumentThumbnail(), id=$documentId, signalIsCancelled: ${signal?.isCanceled}")

        val fileId = getFileIdFromDocumentId(documentId)
        val userId = getUserId(documentId)
        val driveId = getDriveFromDocId(documentId)

        var parcel: ParcelFileDescriptor? = null
        val userDrive = UserDrive(userId.toInt(), driveId.id, comeFromSharedWithMe(documentId))
        FileController.getFileById(fileId, userDrive)?.let { file ->
            val outputFolder = java.io.File(context?.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val name = "${fileId}_${file.name}"
            val outputFile = java.io.File(outputFolder, name)

            try {
                val okHttpClient: OkHttpClient
                runBlocking { okHttpClient = KDriveHttpClient.getHttpClient(userId.toInt()) }
                val response = DownloadWorker.downloadFileResponse(file.thumbnail(), okHttpClient) {}

                if (response.isSuccessful) {
                    DownloadWorker.saveRemoteData(response, outputFile) {
                        parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                } else if (outputFile.exists()) {
                    parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (outputFile.exists()) parcel = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

        }

        return if (parcel == null) super.openDocumentThumbnail(documentId, sizeHint, signal)
        else AssetFileDescriptor(parcel, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
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

    private fun MatrixCursor.addRootDrives(userId: Int, shareFolderID: Int? = null, shareWithMe: Boolean = false) {
        runBlocking {
            DriveInfosController.getDrives(userId, sharedWithMe = shareWithMe).forEach { drive ->
                val driveDocument = "${drive.name}$DRIVE_SEPARATOR${drive.id}"
                val documentId =
                    if (shareFolderID == null) "$userId$SEPARATOR$driveDocument$SEPARATOR${Utils.ROOT_ID}"
                    else "$userId$SEPARATOR$shareFolderID$SEPARATOR$driveDocument$SEPARATOR${Utils.ROOT_ID}"
                addFile(null, documentId, drive.name)
            }
        }
    }

    private fun MatrixCursor.addFile(file: File?, documentId: String, name: String = "") {
        var flags = 0

        if (file?.isFolder() == true) flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE

        val mimetype = if (file == null || file.isFolder()) DocumentsContract.Document.MIME_TYPE_DIR else file.getMimeType()

        if (file?.isFolder() == false && file.hasThumbnail) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
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
                val docID = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val mimetype = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
                val displayName = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                val size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE))
                val flags = cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docID)
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

        val mutex = Mutex()

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

        private fun getDataFile(
            context: Context,
            file: File,
            userDrive: UserDrive,
            accessMode: Int
        ): ParcelFileDescriptor? {
            val cacheFile = file.getCacheFile(context, userDrive)
            val offlineFile = file.getOfflineFile(context, userDrive.userId)

            try {
                if (offlineFile != null && file.isOfflineAndIntact(offlineFile))
                    return ParcelFileDescriptor.open(offlineFile, accessMode)
                if (!file.isObsolete(cacheFile) && file.size == cacheFile.length())
                    return ParcelFileDescriptor.open(cacheFile, accessMode)

                val okHttpClient = runBlocking { KDriveHttpClient.getHttpClient(userDrive.userId) }
                val response = DownloadWorker.downloadFileResponse(
                    ApiRoutes.downloadFile(file),
                    okHttpClient
                ) { progress ->
                    Log.i(TAG, "open currentProgress: $progress")
                }

                if (response.isSuccessful) {
                    DownloadWorker.saveRemoteData(response, cacheFile)
                    cacheFile.setLastModified(file.getLastModifiedInMilliSecond())
                    return ParcelFileDescriptor.open(cacheFile, accessMode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return null
        }

        fun createShareFileUri(context: Context, file: File, userDrive: UserDrive = UserDrive()): Uri? {
            val currentUserId = userDrive.userId
            val currentDriveId = userDrive.driveId

            DriveInfosController.getDrives(currentUserId, currentDriveId, userDrive.sharedWithMe).firstOrNull()?.let { drive ->
                val baseContentUri = "content://${context.getString(R.string.CLOUD_STORAGE_AUTHORITY)}/document/"
                val sharedId = if (drive.sharedWithMe) "${SHARED_WITHME_FOLDER_ID}$SEPARATOR" else ""
                val content = "$currentUserId$SEPARATOR$sharedId${drive.name}$DRIVE_SEPARATOR$currentDriveId/${file.id}"
                return "$baseContentUri${URLEncoder.encode(content, "UTF-8")}".toUri()
            }

            return null
        }

        fun notifyRootsChanged(context: Context) {
            val authority = context.getString(R.string.CLOUD_STORAGE_AUTHORITY)
            val rootsUri = DocumentsContract.buildRootsUri(authority)
            context.contentResolver.notifyChange(rootsUri, null)
        }
    }
}