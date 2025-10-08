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
package com.infomaniak.drive.utils

import androidx.collection.arrayMapOf
import com.infomaniak.core.legacy.api.ApiController
import com.infomaniak.core.legacy.models.ApiResponse
import com.infomaniak.drive.KDriveTest.Companion.context
import com.infomaniak.drive.KDriveTest.Companion.okHttpClient
import com.infomaniak.drive.KDriveTest.Companion.userDrive
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.createFolder
import com.infomaniak.drive.data.api.ApiRepository.postDropBox
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import org.junit.jupiter.api.Assertions
import java.util.UUID

object ApiTestUtils {

    fun assertApiResponseData(response: ApiResponse<*>) {
        with(response) {
            val resultError =
                if (!isSuccess()) "(result: [${error?.code}] - [${error?.description}] - [${context.getString(translatedError)}])"
                else ""
            Assertions.assertTrue(isSuccess(), "This should succeed $resultError")
            Assertions.assertNull(error, "There should be no error")
            Assertions.assertNotNull(data, "The data cannot be null")
        }
    }

    fun deleteTestFile(remoteFile: File) {
        Assertions.assertTrue(
            ApiRepository.deleteFile(remoteFile).isSuccess(),
            "created file couldn't be deleted from the remote",
        )
    }

    fun createFileForTest(): File {
        val createFile = CreateFile("offline doc ${UUID.randomUUID()}", File.Office.DOCS.extension)
        return ApiRepository.createOfficeFile(Env.DRIVE_ID, Utils.ROOT_ID, createFile).let {
            assertApiResponseData(it)
            it.data!!
        }
    }

    fun getCategory(driveId: Int): ApiResponse<Array<Category>> {
        return ApiController.callApi(ApiRoutes.categories(driveId), ApiController.ApiMethod.GET)
    }

    // Creates a file, puts it in trash and returns it
    fun putNewFileInTrash() = createFileForTest().also { deleteTestFile(it) }

    fun createFolderWithName(name: String): File {
        return createFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, name).let {
            assertApiResponseData(it)
            it.data!!
        }
    }

    fun createDropBoxForTest(folder: File, maxSize: Long): DropBox {
        val body = arrayMapOf(
            "email_when_finished" to true,
            "limit_file_size" to maxSize,
            "password" to "password",
        )
        return postDropBox(folder, body).let {
            assertApiResponseData(it)
            it.data!!
        }
    }
}
