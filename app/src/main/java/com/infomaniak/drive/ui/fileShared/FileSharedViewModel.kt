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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileSharedViewModel(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {

    val appContext = getApplication<MainApplication>()
    var rootSharedFile: File? = null
    val childrenLiveData = SingleLiveEvent<List<File>>()

    private val driveId: Int
        inline get() = savedStateHandle.get<Int>(FileSharedActivityArgs::driveId.name) ?: ERROR_ID

    private val fileSharedLinkUuid: String
        inline get() = savedStateHandle.get<String>(FileSharedActivityArgs::fileSharedLinkUuid.name) ?: ""
    
    private val fileId: Int
        inline get() = savedStateHandle.get<Int>(FileSharedActivityArgs::fileId.name) ?: ERROR_ID

    fun downloadSharedFile() = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.getShareLinkFile(driveId, fileSharedLinkUuid, fileId)
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            val sharedFile = apiResponse.data!!
            rootSharedFile = sharedFile
            childrenLiveData.postValue(listOf(sharedFile))
        } else {
            Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
        }
    }

    fun downloadSharedFileChildren(folderId: Int) = viewModelScope.launch(Dispatchers.IO) {
        val apiResponse = ApiRepository.getShareLinkFileChildren(driveId, fileSharedLinkUuid, folderId)
        Log.e("TOTO", "downloadSharedFile: ${apiResponse.isSuccess()}")
        if (apiResponse.isSuccess() && apiResponse.data != null) {
            Log.e("TOTO", "downloadSharedFile: ")
            childrenLiveData.postValue(apiResponse.data!!)
        } else {
            Log.e("TOTO", "downloadSharedFile: ${apiResponse.error?.code}")
        }
    }

    companion object {
        const val ERROR_ID = -1
    }
}