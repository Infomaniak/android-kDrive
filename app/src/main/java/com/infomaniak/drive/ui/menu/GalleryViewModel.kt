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
package com.infomaniak.drive.ui.menu

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.IsComplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GalleryViewModel : ViewModel() {

    private var getGalleryJob: Job? = null

    private var currentCursor: String? = null

    private var lastGalleryFiles = arrayListOf<File>()

    val galleryApiResult = MutableLiveData<Pair<ArrayList<File>, IsComplete>>()
    val needToRestoreFiles get() = galleryApiResult.isInitialized

    fun loadLastGallery(driveId: Int, ignoreCloud: Boolean) {
        lastGalleryFiles = arrayListOf()
        loadLastGallery(driveId, ignoreCloud, isFirstPage = true)
    }

    fun loadMoreGallery(driveId: Int, ignoreCloud: Boolean) {
        currentCursor?.let {
            loadLastGallery(driveId, ignoreCloud, isFirstPage = false, currentCursor)
        }
    }

    fun restoreGalleryFiles() {
        if (needToRestoreFiles) {
            val isComplete = galleryApiResult.value?.second ?: true
            galleryApiResult.value = lastGalleryFiles to isComplete
        }
    }

    private fun loadLastGallery(
        driveId: Int,
        ignoreCloud: Boolean,
        isFirstPage: Boolean,
        cursor: String? = null,
    ) {
        getGalleryJob?.cancel()
        getGalleryJob = viewModelScope.launch(Dispatchers.IO) {
            val result = getLastGallery(driveId, ignoreCloud, isFirstPage, cursor)
            galleryApiResult.postValue(result)
            lastGalleryFiles.addAll(result.first)
        }
    }

    private fun getLastGallery(
        driveId: Int,
        ignoreCloud: Boolean,
        isFirstPage: Boolean,
        cursor: String?,
    ): Pair<ArrayList<File>, IsComplete> {
        getGalleryJob?.cancel()
        getGalleryJob = Job()

        return if (ignoreCloud) emitRealmGallery() else fetchApiGallery(driveId, isFirstPage, cursor)
    }

    private fun emitRealmGallery(): Pair<ArrayList<File>, Boolean> {
        currentCursor = null
        return FileController.getGalleryDrive() to true
    }

    private fun fetchApiGallery(driveId: Int, isFirstPage: Boolean, cursor: String?): Pair<ArrayList<File>, Boolean> {
        val apiResponse = ApiRepository.getLastGallery(driveId = driveId, cursor = cursor)
        return if (apiResponse.isSuccess()) {
            currentCursor = apiResponse.cursor
            emitApiGallery(apiResponse, isFirstPage)
        } else {
            emitRealmGallery()
        }
    }

    private fun emitApiGallery(
        apiResponse: CursorApiResponse<ArrayList<File>>,
        isFirstPage: Boolean,
    ): Pair<ArrayList<File>, Boolean> {
        val data = apiResponse.data

        val results = if (data.isNullOrEmpty()) {
            arrayListOf<File>() to true
        } else {
            FileController.storeGalleryDrive(data, isFirstPage)
            val isComplete = !apiResponse.hasMore
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
