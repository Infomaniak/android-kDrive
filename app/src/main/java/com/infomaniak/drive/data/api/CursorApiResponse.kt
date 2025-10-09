/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.drive.data.api

import com.google.gson.annotations.SerializedName
import com.infomaniak.core.network.models.ApiError
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus

class CursorApiResponse<T>(
    result: ApiResponseStatus = ApiResponseStatus.UNKNOWN,
    data: T? = null,
    error: ApiError? = null,
    responseAt: Long = 0,
    val cursor: String? = null, // TODO: It's only temporarily nullable, pending a change on the API side.
    @SerializedName("has_more")
    val hasMore: Boolean = false,
) : ApiResponse<T>(
    result = result,
    data = data,
    error = error,
    responseAt = responseAt,
) {
    val hasMoreAndCursorExists inline get() = hasMore && cursor != null
}
