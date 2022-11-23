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
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.CategoryRights
import kotlinx.coroutines.Dispatchers

class SelectCategoriesViewModel : ViewModel() {

    lateinit var categoryRights: CategoryRights
    lateinit var filesCategories: List<FileCategory>
    lateinit var selectedCategories: List<Category>
    lateinit var selectedFiles: List<File>

    fun init(
        usageMode: CategoriesUsageMode,
        categories: IntArray?,
        filesIds: IntArray?,
        userDrive: UserDrive,
    ): LiveData<Boolean> = liveData(Dispatchers.IO) {

        categoryRights = if (usageMode == CategoriesUsageMode.SELECTED_CATEGORIES) {
            selectedCategories = DriveInfosController.getCategoriesFromIds(
                userDrive.driveId,
                categories?.toTypedArray() ?: emptyArray()
            )

            CategoryRights()
        } else {
            selectedFiles = filesIds?.toList()?.mapNotNull { fileId ->
                FileController.getFileById(fileId, userDrive)
            } ?: emptyList()

            if (selectedFiles.isEmpty()) {
                emit(false)
                return@liveData
            }

            DriveInfosController.getCategoryRights(userDrive.driveId)
        }

        filesCategories = findCommonCategoriesOfFiles()

        emit(true)
    }

    private fun findCommonCategoriesOfFiles(): List<FileCategory> {
        if (selectedFiles.size == 1) return selectedFiles.single().categories

        fun File.getCategoriesMap() = categories.associateBy { it.categoryId }

        var categoryIdsInCommon = selectedFiles.firstOrNull()?.getCategoriesMap() ?: return emptyList()

        selectedFiles.forEachIndexed { index, file ->
            if (file.categories.isEmpty() || categoryIdsInCommon.isEmpty()) return emptyList()
            if (index == 0) return@forEachIndexed

            val fileCategoriesMap = file.getCategoriesMap()
            categoryIdsInCommon = categoryIdsInCommon.filterKeys { fileCategoriesMap.containsKey(it) }
        }

        return categoryIdsInCommon.values.toList()
    }
}
