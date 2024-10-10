/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.drive.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.fragment.app.Fragment
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.data.models.MediaFolder
import io.realm.Realm
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

object MediaFoldersProvider {

    @SuppressLint("InlinedApi")
    const val IMAGES_BUCKET_ID = MediaStore.Images.Media.BUCKET_ID

    @SuppressLint("InlinedApi")
    const val VIDEO_BUCKET_ID = MediaStore.Video.Media.BUCKET_ID

    @SuppressLint("InlinedApi")
    const val IMAGES_BUCKET_DISPLAY_NAME = MediaStore.Images.Media.BUCKET_DISPLAY_NAME

    @SuppressLint("InlinedApi")
    const val VIDEO_BUCKET_DISPLAY_NAME = MediaStore.Video.Media.BUCKET_DISPLAY_NAME

    val imagesExternalUri: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val videosExternalUri: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val audiosExternalUri: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val MEDIA_PATH_COLUMN =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
        else MediaStore.MediaColumns.DATA

    private const val imagesSortOrder = "$IMAGES_BUCKET_DISPLAY_NAME ASC"
    private val imagesProjection = arrayOf(
        IMAGES_BUCKET_DISPLAY_NAME,
        IMAGES_BUCKET_ID,
        MEDIA_PATH_COLUMN
    )

    private const val videosSortOrder = "$VIDEO_BUCKET_DISPLAY_NAME ASC"
    private val videosProjection = arrayOf(
        VIDEO_BUCKET_DISPLAY_NAME,
        VIDEO_BUCKET_ID,
        MEDIA_PATH_COLUMN
    )

    /**
     * Need Write permission if is called from [Fragment] or [Activity]
     */
    fun getAllMediaFolders(realm: Realm, contentResolver: ContentResolver, coroutineScope: Job? = null): List<MediaFolder> {
        val mediaFolders = getImageFolders(realm, contentResolver, coroutineScope)
        mediaFolders.putAll(getVideoFolders(realm, contentResolver, coroutineScope) as Map<Long, MediaFolder>)
        return mediaFolders.values.sortedBy { it.name }
    }

    private fun getImageFolders(
        realm: Realm,
        contentResolver: ContentResolver,
        coroutineScope: Job?,
    ): ArrayMap<Long, MediaFolder> {
        val folders = arrayMapOf<Long, MediaFolder>()

        contentResolver.query(imagesExternalUri, imagesProjection, null, null, imagesSortOrder)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineScope?.ensureActive()
                    val folderName = cursor.getString(cursor.getColumnIndexOrThrow(IMAGES_BUCKET_DISPLAY_NAME)) ?: ""
                    val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(IMAGES_BUCKET_ID))
                    cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_PATH_COLUMN))?.let { path ->
                        getLocalMediaFolder(
                            realm = realm,
                            folderId = folderId,
                            folderName = folderName,
                            path = path,
                            coroutineScope = coroutineScope,
                        )?.let { folders[folderId] = it }
                    }
                }
            }
        return folders
    }

    private fun getVideoFolders(
        realm: Realm,
        contentResolver: ContentResolver,
        coroutineScope: Job?,
    ): ArrayMap<Long, MediaFolder> {
        val folders = arrayMapOf<Long, MediaFolder>()

        contentResolver.query(videosExternalUri, videosProjection, null, null, videosSortOrder)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineScope?.ensureActive()
                    val folderName = cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_BUCKET_DISPLAY_NAME)) ?: ""
                    val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_BUCKET_ID))
                    cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_PATH_COLUMN))?.let { path ->
                        getLocalMediaFolder(
                            realm = realm,
                            folderId = folderId,
                            folderName = folderName,
                            path = path,
                            coroutineScope = coroutineScope,
                        )?.let { folders[folderId] = it }
                    }
                }
            }
        return folders
    }

    private fun getLocalMediaFolder(
        realm: Realm,
        folderId: Long,
        folderName: String,
        path: String,
        coroutineScope: Job?,
    ): MediaFolder? {
        coroutineScope?.ensureActive()
        if (path.startsWith("Android/media/${BuildConfig.APPLICATION_ID}")) return null
        return MediaFolder.findById(realm, folderId)?.let { mediaFolder ->
            mediaFolder.apply { if (mediaFolder.name != folderName) mediaFolder.storeOrUpdate() }
        } ?: let {
            val isFirstConfiguration = !AccountUtils.isEnableAppSync()
            val isSynced = isFirstConfiguration && path.contains("${Environment.DIRECTORY_DCIM}/")
            MediaFolder(
                id = folderId,
                name = folderName,
                isSynced = isSynced,
                path = path
            ).apply { storeOrUpdate() }
        }
    }
}
