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
package com.infomaniak.drive.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive

object MediaUtils {

    fun File.isMedia(): Boolean {
        val fileType = getFileType()
        return fileType == ConvertedType.IMAGE || fileType == ConvertedType.VIDEO || fileType == ConvertedType.AUDIO
    }

    fun scanFile(context: Context, file: java.io.File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
    }

    fun File.deleteInMediaScan(context: Context, userDrive: UserDrive = UserDrive()) {
        val (uri, column) = when (getFileType()) {
            ConvertedType.IMAGE -> MediaFoldersProvider.imagesExternalUri to MediaStore.Images.Media.DATA
            ConvertedType.VIDEO -> MediaFoldersProvider.videosExternalUri to MediaStore.Video.Media.DATA
            ConvertedType.AUDIO -> MediaFoldersProvider.audiosExternalUri to MediaStore.Audio.Media.DATA
            else -> throw UnsupportedOperationException("Accept only media files")
        }
        getOfflineFile(context, userDrive.userId)?.let { offlineFile ->
            context.contentResolver.delete(uri, "$column=?", arrayOf(offlineFile.path))
        }
    }
}
