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
package com.infomaniak.drive.utils

import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiController
import kotlinx.android.parcel.RawValue
import org.junit.jupiter.api.Assertions
import java.util.*

object ApiTestUtils {

    fun assertApiResponse(response: ApiResponse<*>) {
        Assertions.assertTrue(response.isSuccess(), "This should succeed")
        Assertions.assertNull(response.error, "There should be no error")
        Assertions.assertNotNull(response.data, "The data cannot be null")
    }

    fun deleteTestFile(remoteFile: File) {
        val deleteResponse = ApiRepository.deleteFile(remoteFile)
        Assertions.assertTrue(deleteResponse.isSuccess(), "created file couldn't be deleted from the remote")
    }

    fun createFileForTest(): File {
        val createFile = CreateFile("offline doc ${UUID.randomUUID()}", File.Office.DOCS.extension)
        val apiResponse = ApiRepository.createOfficeFile(Env.DRIVE_ID, Utils.ROOT_ID, createFile)
        assertApiResponse(apiResponse)
        return apiResponse.data!!
    }

    fun getShareLink(file: File): ApiResponse<ShareLink> {
        return ApiController.callApi(ApiRoutes.shareLink(file), ApiController.ApiMethod.GET)
    }

    fun getCategory(driveId: Int): ApiResponse<Array<Category>> {
        return ApiController.callApi(ApiRoutes.createCategory(driveId), ApiController.ApiMethod.GET)
    }
}
