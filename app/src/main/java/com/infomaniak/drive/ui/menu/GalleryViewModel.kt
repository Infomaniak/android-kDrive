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
package com.infomaniak.drive.ui.menu

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GalleryViewModel : ViewModel() {

    private var getGalleryJob: Job? = null

    private var currentCursor: String? = null

    val galleryApiResult = MutableLiveData<Pair<ArrayList<File>, IsComplete>?>()

    fun loadLastGallery(driveId: Int, ignoreCloud: Boolean) {
        getGalleryJob?.cancel()
        getGalleryJob = viewModelScope.launch(Dispatchers.IO) {
            galleryApiResult.postValue(getLastGallery(driveId, ignoreCloud, isFirstPage = true))
        }
    }

    fun loadMoreGallery(driveId: Int, ignoreCloud: Boolean) {
        currentCursor?.let {
            getGalleryJob?.cancel()
            getGalleryJob = viewModelScope.launch(Dispatchers.IO) {
                galleryApiResult.postValue(getLastGallery(driveId, ignoreCloud, isFirstPage = false, currentCursor))
            }
        }
    }

    private fun getLastGallery(
        driveId: Int,
        ignoreCloud: Boolean = false,
        isFirstPage: Boolean = true,
        cursor: String? = null,
    ): Pair<ArrayList<File>, IsComplete>? {
        getGalleryJob?.cancel()
        getGalleryJob = Job()

        return if (ignoreCloud) emitRealmGallery() else fetchApiGallery(driveId, isFirstPage, cursor)
    }

    private fun emitRealmGallery(): Pair<ArrayList<File>, Boolean> {
        currentCursor = null
        return FileController.getGalleryDrive() to true
    }

    private fun fetchApiGallery(driveId: Int, isFirstPage: Boolean, cursor: String?): Pair<java.util.ArrayList<File>, Boolean>? {
        val apiResponse = ApiRepository.getLastGallery(driveId = driveId, cursor = cursor)
        return if (apiResponse.isSuccess()) {
            currentCursor = apiResponse.cursor
            emitApiGallery(apiResponse, isFirstPage)
        } else emitRealmGallery()
    }

    private fun emitApiGallery(
        apiResponse: ApiResponse<ArrayList<File>>,
        isFirstPage: Boolean,
    ): Pair<ArrayList<File>, Boolean>? {
        val data = apiResponse.data

        val results = if (data.isNullOrEmpty()) {
            null
        } else {
            FileController.storeGalleryDrive(data, isFirstPage)
            val isComplete = apiResponse.cursor == null
            data to isComplete
        }

        if (isFirstPage) FileController.removeOrphanFiles()
        return results
    }

    override fun onCleared() {
        getGalleryJob?.cancel()
        super.onCleared()
    }
}
