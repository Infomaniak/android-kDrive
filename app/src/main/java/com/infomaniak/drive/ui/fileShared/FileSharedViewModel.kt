/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileShared

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileSharedViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    var rootSharedFile: File? = null
    val childrenLiveData = SingleLiveEvent<List<File>>()

    private val driveId: Int
        inline get() = savedStateHandle.get<Int>(FileSharedActivityArgs::driveId.name) ?: ROOT_SHARED_FILE_ID

    private val fileSharedLinkUuid: String
        inline get() = savedStateHandle.get<String>(FileSharedActivityArgs::fileSharedLinkUuid.name) ?: ""

    private val fileId: Int
        inline get() = savedStateHandle.get<Int>(FileSharedActivityArgs::fileId.name) ?: ROOT_SHARED_FILE_ID

    fun downloadSharedFile() = viewModelScope.launch(Dispatchers.IO) {
        val files = if (fileId == ROOT_SHARED_FILE_ID) {
            rootSharedFile?.let(::listOf) ?: listOf()
        } else {
            val apiResponse = ApiRepository.getShareLinkFile(driveId, fileSharedLinkUuid, fileId)
            if (apiResponse.isSuccess() && apiResponse.data != null) {
                val sharedFile = apiResponse.data!!
                rootSharedFile = sharedFile
                listOf(sharedFile)
            } else {
                Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
                listOf()
            }
        }

        childrenLiveData.postValue(files)
    }

    fun downloadSharedFileChildren(folderId: Int, sortType: SortType) = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.getShareLinkFileChildren(driveId, fileSharedLinkUuid, folderId, sortType)
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            childrenLiveData.postValue(apiResponse.data!!)
        } else {
            Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
            childrenLiveData.postValue(emptyList())
        }
    }

    companion object {
        const val ROOT_SHARED_FILE_ID = 1
    }
}