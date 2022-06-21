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
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.find
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import java.util.*

class SelectCategoriesViewModel : ViewModel() {

    fun addCategory(file: File, categoryId: Int): LiveData<ApiResponse<Unit>> {
        return liveData(Dispatchers.IO) {
            with(ApiRepository.addCategory(file, categoryId)) {
                if (isSuccess()) {
                    FileController.updateFile(file.id) {
                        it.categories?.add(FileCategory(categoryId, userId = AccountUtils.currentUserId, addedToFileAt = Date()))
                    }
                }
                emit(this)
            }
        }
    }

    fun removeCategory(file: File, categoryId: Int): LiveData<ApiResponse<Unit>> {
        return liveData(Dispatchers.IO) {
            with(ApiRepository.removeCategory(file, categoryId)) {
                if (isSuccess()) {
                    FileController.updateFile(file.id) { localFile ->
                        localFile.categories?.find(categoryId)?.deleteFromRealm()
                    }
                }
                emit(this)
            }
        }
    }
}
