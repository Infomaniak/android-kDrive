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
package com.infomaniak.drive.ui.fileList.fileDetails

import androidx.lifecycle.*
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.CursorApiResponse
import com.infomaniak.drive.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class FileDetailsViewModel : ViewModel() {

    val currentFile = MutableLiveData<File>()
    val currentFileShare = MutableLiveData<Share>()

    private var getFileCommentsJob = Job()
    private var getFileActivitiesJob = Job()

    fun getFileActivities(file: File): LiveData<CursorApiResponse<ArrayList<FileActivity>>?> {
        getFileActivitiesJob.cancel()
        getFileActivitiesJob = Job()

        return liveData(Dispatchers.IO + getFileActivitiesJob) {
            manageRecursiveApiResponse(file) { file, cursor ->
                ApiRepository.getFileActivities(file, cursor, forFileList = false)
            }
        }
    }

    fun getFileComments(file: File): LiveData<CursorApiResponse<ArrayList<FileComment>>?> {
        getFileCommentsJob.cancel()
        getFileCommentsJob = Job()

        return liveData(Dispatchers.IO + getFileCommentsJob) {
            manageRecursiveApiResponse(file) { file, cursor -> ApiRepository.getFileComments(file, cursor) }
        }
    }

    fun getFileCounts(folder: File): LiveData<FileCount> = liveData(Dispatchers.IO) {
        ApiRepository.getFileCount(folder).data?.let { emit(FileCount(it.files, it.count, it.folders)) }
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
        emit(ApiRepository.postLikeComment(file, fileComment.id))
    }

    fun postUnlike(file: File, fileComment: FileComment) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postUnlikeComment(file, fileComment.id))
    }

    private suspend fun <T> LiveDataScope<CursorApiResponse<ArrayList<T>>?>.manageRecursiveApiResponse(
        file: File,
        apiResponseCallback: suspend (file: File, cursor: String?) -> CursorApiResponse<ArrayList<T>>
    ) {
        suspend fun recursive(cursor: String?) {
            with(apiResponseCallback(file, cursor)) {
                if (isSuccess()) {
                    when {
                        data.isNullOrEmpty() -> emit(null)
                        hasMore -> {
                            emit(this)
                            recursive(this.cursor)
                        }
                        else -> emit(this)
                    }
                }
            }
        }
        recursive(cursor = null)
    }
}
