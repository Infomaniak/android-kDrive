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

import androidx.lifecycle.*
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.drive.utils.isLastPage
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class PicturesViewModel : ViewModel() {
    private var getPicturesJob: Job = Job()

    var lastPicturesPage = 1
    var lastPicturesLastPage = 1

    val loadMorePictures = MutableLiveData<Pair<Int, Boolean>>()
    val picturesApiResult = Transformations.switchMap(loadMorePictures) { (driveId, ignoreCloud) ->
        getLastPictures(driveId, ignoreCloud)
    }

    private fun getLastPictures(
        driveId: Int,
        ignoreCloud: Boolean = false,
    ): LiveData<Pair<ArrayList<File>, IsComplete>?> {
        getPicturesJob.cancel()
        getPicturesJob = Job()

        return liveData(Dispatchers.IO + getPicturesJob) {
            if (ignoreCloud) emitRealmPictures() else fetchApiPictures(driveId)
        }
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.emitRealmPictures() {
        emit(FileController.getPicturesDrive() to true)
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.fetchApiPictures(driveId: Int) {
        val page = lastPicturesPage
        val apiResponse = ApiRepository.getLastPictures(driveId = driveId, page = page)
        if (apiResponse.isSuccess()) emitApiPictures(apiResponse, page) else emitRealmPictures()
    }

    private suspend fun LiveDataScope<Pair<ArrayList<File>, IsComplete>?>.emitApiPictures(
        apiResponse: ApiResponse<ArrayList<File>>,
        page: Int,
    ) {
        val data = apiResponse.data
        val isFirstPage = page == 1
        val isComplete = apiResponse.isLastPage()

        if (data.isNullOrEmpty()) {
            emit(null)
        } else {
            FileController.storePicturesDrive(data, isFirstPage)
            emit(data to isComplete)
        }

        if (isFirstPage) FileController.removeOrphanFiles()
    }

    override fun onCleared() {
        getPicturesJob.cancel()
        super.onCleared()
    }
}
