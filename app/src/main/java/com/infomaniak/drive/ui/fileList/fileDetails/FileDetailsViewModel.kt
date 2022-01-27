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
package com.infomaniak.drive.ui.fileList.fileDetails

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.data.models.FileComment
import com.infomaniak.drive.data.models.Share
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class FileDetailsViewModel : ViewModel() {

    val currentFile = MutableLiveData<File>()
    val currentFileShare = MutableLiveData<Share>()

    private var getFileCommentsJob = Job()
    private var getFileActivitiesJob = Job()

    fun getFileActivities(file: File): LiveData<ApiResponse<ArrayList<FileActivity>>?> {
        getFileActivitiesJob.cancel()
        getFileActivitiesJob = Job()

        return liveData(Dispatchers.IO + getFileActivitiesJob) {
            suspend fun recursive(page: Int) {
                val apiRepository = ApiRepository.getFileActivities(file, page)
                if (apiRepository.isSuccess()) {
                    when {
                        apiRepository.data?.isNullOrEmpty() == true -> emit(null)
                        apiRepository.page == apiRepository.pages -> emit(apiRepository)
                        else -> {
                            emit(apiRepository)
                            recursive(page + 1)
                        }
                    }
                }
            }
            recursive(1)
        }
    }

    fun getFileComments(file: File): LiveData<ApiResponse<ArrayList<FileComment>>?> {
        getFileCommentsJob.cancel()
        getFileCommentsJob = Job()

        return liveData(Dispatchers.IO + getFileCommentsJob) {
            suspend fun recursive(page: Int) {
                val apiRepository = ApiRepository.getFileComments(file, page)
                if (apiRepository.isSuccess()) {
                    when {
                        apiRepository.data?.isNullOrEmpty() == true -> emit(null)
                        apiRepository.page == apiRepository.pages -> emit(apiRepository)
                        else -> {
                            emit(apiRepository)
                            recursive(page + 1)
                        }
                    }
                }
            }
            recursive(1)
        }
    }

    fun postFileComment(file: File, body: String) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileComment(file, body))
    }

    fun putFileComment(file: File, commentId: Int, body: String) = liveData(Dispatchers.IO) {
        emit(ApiRepository.putFileComment(file, commentId, body))
    }

    fun deleteFileComment(file: File, commentId: Int) = liveData(Dispatchers.IO) {
        emit(ApiRepository.deleteFileComment(file, commentId))
    }

    fun postLike(file: File, fileComment: FileComment) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileCommentLike(file, fileComment.id))
    }

    fun postUnlike(file: File, fileComment: FileComment) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileCommentUnlike(file, fileComment.id))
    }
}