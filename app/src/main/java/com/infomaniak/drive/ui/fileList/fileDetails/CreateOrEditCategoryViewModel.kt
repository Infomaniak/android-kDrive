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

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.find
import kotlinx.coroutines.Dispatchers

class CreateOrEditCategoryViewModel : ViewModel() {
    lateinit var selectedFiles: List<File>

    val driveId: Int by lazy { selectedFiles.first().driveId }

    fun init(filesId: IntArray?): LiveData<Boolean> = liveData(Dispatchers.IO) {
        selectedFiles = filesId?.toList()?.mapNotNull { fileId -> FileController.getFileById(fileId) } ?: emptyList()
        emit(selectedFiles.isEmpty())
    }

    fun createCategory(name: String, color: String) = liveData(Dispatchers.IO) {
        with(ApiRepository.createCategory(driveId, name, color)) {
            if (isSuccess()) DriveInfosController.updateDrive { it.categories.add(data) }
            emit(this)
        }
    }

    fun editCategory(categoryId: Int, name: String?, color: String) = liveData(Dispatchers.IO) {
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
