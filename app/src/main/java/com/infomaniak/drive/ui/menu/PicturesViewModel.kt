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
package com.infomaniak.drive.ui.menu

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class PicturesViewModel : ViewModel() {
    private var getPicturesJob: Job = Job()

    fun getAllPicturesFiles(
        driveId: Int,
        ignoreCloud: Boolean = false
    ): LiveData<Pair<ArrayList<File>, Boolean>> {
        getPicturesJob.cancel()
        getPicturesJob = Job()
        return liveData(Dispatchers.IO + getPicturesJob) {
            suspend fun recursive(page: Int) {
                if (!ignoreCloud) {
                    val apiResponse = ApiRepository.getLastPictures(driveId = driveId, page = page)
                    if (apiResponse.isSuccess()) {
                        val data = apiResponse.data
                        when {
                            data == null -> emit(null)
                            data.size < ApiRepository.PER_PAGE -> emit(data to true)
                            else -> {
                                emit(data to false)
                                recursive(page + 1)
                            }
                        }
                    } else emit(null)
                }
            }
            recursive(1)
        }
    }

    fun cancelPicturesJob() {
        getPicturesJob.cancel()
    }

}