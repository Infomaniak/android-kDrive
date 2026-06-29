/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.KDriveTest.Companion.context
import org.junit.jupiter.api.Assertions

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
}
