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
package com.infomaniak.drive.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.fragment.app.Fragment
import com.infomaniak.drive.data.models.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaFoldersProvider {

    @SuppressLint("InlinedApi")
    const val IMAGES_BUCKET_ID = MediaStore.Images.Media.BUCKET_ID

    @SuppressLint("InlinedApi")
    const val VIDEO_BUCKET_ID = MediaStore.Video.Media.BUCKET_ID

    @SuppressLint("InlinedApi")
    const val IMAGES_BUCKET_DISPLAY_NAME = MediaStore.Images.Media.BUCKET_DISPLAY_NAME

    @SuppressLint("InlinedApi")
    const val VIDEO_BUCKET_DISPLAY_NAME = MediaStore.Video.Media.BUCKET_DISPLAY_NAME

    private val MEDIA_PATH_COLUMN =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
        else MediaStore.MediaColumns.DATA


    private val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private const val imagesSortOrder = "$IMAGES_BUCKET_DISPLAY_NAME ASC"
    private val imagesProjection = arrayOf(
        IMAGES_BUCKET_DISPLAY_NAME,
        IMAGES_BUCKET_ID,
        MEDIA_PATH_COLUMN
    )

    private val videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    private const val videosSortOrder = "$VIDEO_BUCKET_DISPLAY_NAME ASC"
    private val videosProjection = arrayOf(
        VIDEO_BUCKET_DISPLAY_NAME,
        VIDEO_BUCKET_ID,
        MEDIA_PATH_COLUMN
    )

    /**
     * Need Write permission if is called from [Fragment] or [Activity]
     */
    suspend fun getAllMediaFolders(contentResolver: ContentResolver): List<MediaFolder> = withContext(Dispatchers.IO) {
        val mediaFolders = getImageFolders(contentResolver)
        mediaFolders.putAll(getVideoFolders(contentResolver) as Map<Long, MediaFolder>)
        mediaFolders.values.toList()
    }

    private fun getImageFolders(contentResolver: ContentResolver): ArrayMap<Long, MediaFolder> {
        val folders = arrayMapOf<Long, MediaFolder>()
        contentResolver.query(imagesUri, imagesProjection, null, null, imagesSortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val folderName = cursor.getString(cursor.getColumnIndexOrThrow(IMAGES_BUCKET_DISPLAY_NAME)) ?: ""
                val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(IMAGES_BUCKET_ID))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_PATH_COLUMN))
                folders[folderId] = getLocalMediaFolder(folderId, folderName, path)
            }
        }
        return folders
    }

    private fun getVideoFolders(contentResolver: ContentResolver): ArrayMap<Long, MediaFolder> {
        val folders = arrayMapOf<Long, MediaFolder>()
        contentResolver.query(videosUri, videosProjection, null, null, videosSortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val folderName = cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_BUCKET_DISPLAY_NAME)) ?: ""
                val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_BUCKET_ID))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_PATH_COLUMN))
                folders[folderId] = getLocalMediaFolder(folderId, folderName, path)
            }
        }
        return folders
    }

    private fun getLocalMediaFolder(folderId: Long, folderName: String, path: String): MediaFolder {
        return MediaFolder.findById(folderId)?.let { mediaFolder ->
            mediaFolder.apply { if (mediaFolder.name != folderName) mediaFolder.storeOrUpdate() }
        } ?: let {
            val isSynced = path.contains("${Environment.DIRECTORY_DCIM}/")
            MediaFolder(folderId, folderName, isSynced).apply { storeOrUpdate() }
        }
    }
}