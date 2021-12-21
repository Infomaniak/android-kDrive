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
package com.infomaniak.drive.ui.fileList.categories

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*

class SelectCategoriesViewModel : ViewModel() {

    private var addCategoryJob = Job()
    private var removeCategoryJob = Job()

    fun addCategory(file: File, categoryId: Int): LiveData<ApiResponse<Unit>> {
        addCategoryJob.cancel()
        addCategoryJob = Job()
        return liveData(Dispatchers.IO + addCategoryJob) {
            with(ApiRepository.addCategory(file, categoryId)) {
                if (isSuccess()) {
                    FileController.updateFile(file.id) {
                        it.categories.add(FileCategory(categoryId, userId = AccountUtils.currentUserId, addedToFileAt = Date()))
                    }
                }
                emit(this)
            }
        }
    }

    fun removeCategory(file: File, categoryId: Int): LiveData<ApiResponse<Unit>> {
        removeCategoryJob.cancel()
        removeCategoryJob = Job()
        return liveData(Dispatchers.IO + removeCategoryJob) {
            with(ApiRepository.removeCategory(file, categoryId)) {
                if (isSuccess()) {
                    FileController.updateFile(file.id) { localFile ->
                        val categories = localFile.categories
                        val category = categories.find { it.id == categoryId }
                        categories.remove(category)
                    }
                }
                emit(this)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        addCategoryJob.cancel()
        removeCategoryJob.cancel()
    }
}
