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

import android.content.ContentResolver
import android.provider.MediaStore
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf

object MediaFoldersProvider {

    val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val imagesSortOrder = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " ASC"
    private val imagesProjection = arrayOf(
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID
    )

    val videosUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videosSortOrder = MediaStore.Video.Media.BUCKET_DISPLAY_NAME + " ASC"
    private val videosProjection = arrayOf(
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.BUCKET_ID
    )

    fun getAllMediaFolders(contentResolver: ContentResolver) {
        val mediaFolders = getImageFolders(contentResolver).apply {
            putAll(getVideoFolders(contentResolver) as Map<Long, String>)
        }

    }

    private fun getImageFolders(contentResolver: ContentResolver): ArrayMap<Long, String> {
        val folders = arrayMapOf<Long, String>()
        contentResolver.query(imagesUri, imagesProjection, null, null, imagesSortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
                folders[folderId] = folderName
            }
        }
        return folders
    }

    private fun getVideoFolders(contentResolver: ContentResolver): ArrayMap<Long, String> {
        val folders = arrayMapOf<Long, String>()
        contentResolver.query(videosUri, videosProjection, null, null, videosSortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME))
                val folderId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID))
                folders[folderId] = folderName
            }
        }
        return folders
    }
}