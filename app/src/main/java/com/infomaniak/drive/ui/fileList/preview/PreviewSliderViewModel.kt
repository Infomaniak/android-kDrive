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
package com.infomaniak.drive.ui.fileList.preview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.models.UserDrive

internal fun previewPositionAfterRemoval(removedPosition: Int, selectedPosition: Int, remainingCount: Int): Int {
    require(remainingCount > 0)
    return selectedPosition.takeIf { it >= 0 } ?: removedPosition.coerceAtMost(remainingCount - 1)
}

class PreviewSliderViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val pdfIsDownloading = MutableLiveData<Boolean>()
    var currentFileId: Int
        get() = savedStateHandle[CURRENT_FILE_ID] ?: requireNotNull(savedStateHandle["fileId"])
        set(value) { savedStateHandle[CURRENT_FILE_ID] = value }

    var currentDriveId: Int?
        get() = savedStateHandle[CURRENT_DRIVE_ID] ?: savedStateHandle["driveId"]
        set(value) { savedStateHandle[CURRENT_DRIVE_ID] = value }

    var fileIds: IntArray?
        get() = savedStateHandle["fileIds"]
        set(value) { savedStateHandle["fileIds"] = value }

    var fileDriveIds: IntArray?
        get() = savedStateHandle["fileDriveIds"]
        set(value) { savedStateHandle["fileDriveIds"] = value }

    val previewListToken: String?
        get() = savedStateHandle["previewListToken"]

    fun fileKeys(defaultFileId: Int, defaultDriveId: Int): List<Pair<Int, Int>> {
        val ids = fileIds ?: intArrayOf(defaultFileId)
        val drives = fileDriveIds ?: IntArray(ids.size) { defaultDriveId }
        require(ids.size == drives.size) { "Preview file and drive lists have different sizes" }
        return ids.indices.map { ids[it] to drives[it] }
    }

    fun removeFile(fileId: Int, driveId: Int) {
        val ids = fileIds ?: return
        val drives = fileDriveIds
        if (drives == null) {
            fileIds = ids.filterNot { it == fileId }.toIntArray()
        } else {
            require(ids.size == drives.size) { "Preview file and drive lists have different sizes" }
            val retained = ids.indices.filterNot { ids[it] == fileId && drives[it] == driveId }
            fileIds = retained.map { ids[it] }.toIntArray()
            fileDriveIds = retained.map { drives[it] }.toIntArray()
        }
    }

    val parentFolderId: Int
        get() = savedStateHandle["parentFolderId"] ?: -1

    var addedFileIds: IntArray
        get() = savedStateHandle["addedPreviewFileIds"] ?: intArrayOf()
        set(value) { savedStateHandle["addedPreviewFileIds"] = value }

    var removedFileIds: IntArray
        get() = savedStateHandle["removedPreviewFileIds"] ?: intArrayOf()
        set(value) { savedStateHandle["removedPreviewFileIds"] = value }

    lateinit var userDrive: UserDrive
    var publicShareCanDownload = true
    var onPreviewClosed: (() -> Unit)? = null

    override fun onCleared() {
        onPreviewClosed?.invoke()
        super.onCleared()
    }

    private companion object {
        const val CURRENT_FILE_ID = "currentPreviewFileId"
        const val CURRENT_DRIVE_ID = "currentPreviewDriveId"
    }
}
