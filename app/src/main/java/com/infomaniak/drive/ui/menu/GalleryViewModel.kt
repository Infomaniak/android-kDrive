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

import androidx.lifecycle.*
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.drive.utils.isLastPage
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class GalleryViewModel : ViewModel() {
    private var getGalleryJob: Job = Job()

    var lastGalleryPage = 1
    var lastGalleryLastPage = 1

    val loadMoreGallery = MutableLiveData<Pair<Int, Boolean>>()
    val galleryApiResult = loadMoreGallery.switchMap { (driveId, ignoreCloud) ->
        getLastGallery(driveId, ignoreCloud)
    }

    private fun getLastGallery(
        driveId: Int,
        ignoreCloud: Boolean = false,
    ): LiveData<Pair<ArrayList<File>, IsComplete>?> {
        getGalleryJob.cancel()
        getGalleryJob = Job()

        return liveData(Dispatchers.IO + getGalleryJob) {
            if (ignoreCloud) emitRealmGallery() else fetchApiGallery(driveId)
        }
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.emitRealmGallery() {
        emit(FileController.getGalleryDrive() to true)
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.fetchApiGallery(driveId: Int) {
        val page = lastGalleryPage
        val apiResponse = ApiRepository.getLastGallery(driveId = driveId, page = page)
        if (apiResponse.isSuccess()) emitApiGallery(apiResponse, page) else emitRealmGallery()
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.emitApiGallery(
        apiResponse: ApiResponse<ArrayList<File>>,
        page: Int,
    ) {
        val data = apiResponse.data
        val isFirstPage = page == 1

        if (data.isNullOrEmpty()) {
            emit(null)
        } else {
            FileController.storeGalleryDrive(data, isFirstPage)
            val isComplete = apiResponse.isLastPage()
            emit(data to isComplete)
        }

        if (isFirstPage) FileController.removeOrphanFiles()
    }

    override fun onCleared() {
        getGalleryJob.cancel()
        super.onCleared()
    }
}
