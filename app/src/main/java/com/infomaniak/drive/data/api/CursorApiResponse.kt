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
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.models.IApiResponse

data class CursorApiResponse<T>(
    override val result: ApiResponseStatus = ApiResponseStatus.UNKNOWN,
    override val data: T? = null,
    override val error: ApiError? = null,
    @SerializedName("response_at")
    override val responseAt: Long = 0,
    val cursor: String? = null, // TODO: It's only temporarily nullable, pending a change on the API side.
    @SerializedName("has_more")
    val hasMore: String? = null,
    override var translatedError: Int = 0,
) : IApiResponse<T>