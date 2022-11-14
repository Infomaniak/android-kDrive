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
package com.infomaniak.drive.ui.fileList.fileDetails

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.find
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers

class CreateOrEditCategoryViewModel : ViewModel() {
    val driveId: Int
        get() = selectedFiles.firstOrNull()?.driveId ?: -1

    lateinit var selectedFiles: List<File>

    fun init(filesId: IntArray?): LiveData<Boolean> = liveData(Dispatchers.IO) {
        selectedFiles = filesId?.toList()?.mapNotNull { fileId -> FileController.getFileById(fileId) } ?: emptyList()
        if (selectedFiles.isEmpty()) emit(true)
    }

    fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> {
        return liveData(Dispatchers.IO) {
            with(ApiRepository.createCategory(driveId, name, color)) {
                if (isSuccess()) DriveInfosController.updateDrive { it.categories.add(data) }
                emit(this)
            }
        }
    }

    fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): LiveData<ApiResponse<Category>> {
        return liveData(Dispatchers.IO) {
            with(ApiRepository.editCategory(driveId, categoryId, name, color)) {
                if (isSuccess()) {
                    DriveInfosController.updateDrive { localDrive ->
                        localDrive.categories.apply {
                            find(categoryId)?.deleteFromRealm()
                            add(data)
                        }
                    }
                }
                emit(this)
            }
        }
    }
}
