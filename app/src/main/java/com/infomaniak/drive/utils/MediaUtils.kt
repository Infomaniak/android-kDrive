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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive

object MediaUtils {

    fun File.isMedia(): Boolean {
        val fileType = getFileType()
        return fileType == File.ConvertedType.IMAGE
                || fileType == File.ConvertedType.VIDEO
                || fileType == File.ConvertedType.AUDIO
    }

    fun File.triggerMediaScan(context: Context, offlineFile: java.io.File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType())
                put(MediaStore.MediaColumns.TITLE, name)
                put(MediaStore.MediaColumns.DATE_ADDED, lastModifiedAt)
                put(MediaStore.MediaColumns.DATE_MODIFIED, lastModifiedAt)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.MediaColumns.RELATIVE_PATH, offlineFile.path)
            }
            val uri = when (getFileType()) {
                File.ConvertedType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                File.ConvertedType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                File.ConvertedType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else -> throw UnsupportedOperationException("Accept only media files")
            }
            context.contentResolver.insert(uri, values)
        } else {
            Intent(ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(offlineFile)
                context.sendBroadcast(this)
            }
        }
    }

    fun File.deleteInMediaScan(context: Context, userDrive: UserDrive = UserDrive()) {
        val uri = when (getFileType()) {
            File.ConvertedType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            File.ConvertedType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            File.ConvertedType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> throw UnsupportedOperationException("Accept only media files")
        }
        val column =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
            else MediaStore.Images.Media.DATA
        val path = getOfflineFile(context, userDrive).path
        context.contentResolver.delete(uri, "$column=?", arrayOf(path))
    }
}