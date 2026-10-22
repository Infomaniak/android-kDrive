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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * It extends [ApiResponse] so that Core can still set the headers and the translated error on it. The superclass properties
 * are (de)serialized by [ApiResponse]'s own serializer, so the primary constructor only declares the cursor fields and a
 * transient holder used to feed the superclass when the object is built by hand through the public constructor.
 */
@Serializable
class CursorApiResponse<T> private constructor(
    val cursor: String? = null, // TODO: It's only temporarily nullable, pending a change on the API side.
    @SerialName("has_more")
    @SerializedName("has_more")
    val hasMore: Boolean = false,
    @Transient private val base: ApiResponse<T>? = null,
) : ApiResponse<T>(
    result = base?.result ?: ApiResponseStatus.UNKNOWN,
    data = base?.data,
    uri = base?.uri,
    error = base?.error,
    page = base?.page ?: 0,
    pages = base?.pages ?: 0,
    responseAt = base?.responseAt ?: 0,
    total = base?.total ?: 0,
    itemsPerPage = base?.itemsPerPage ?: 0,
) {

    @JvmOverloads // Keeps the no-arg constructor Gson relies on
    constructor(
        result: ApiResponseStatus = ApiResponseStatus.UNKNOWN,
        data: T? = null,
        error: ApiError? = null,
        responseAt: Long = 0,
        cursor: String? = null,
        hasMore: Boolean = false,
        uri: String? = null,
        page: Int = 0,
        pages: Int = 0,
        total: Int = 0,
        itemsPerPage: Int = 0,
    ) : this(
        cursor = cursor,
        hasMore = hasMore,
        base = ApiResponse(
            result = result,
            data = data,
            uri = uri,
            error = error,
            page = page,
            pages = pages,
            responseAt = responseAt,
            total = total,
            itemsPerPage = itemsPerPage,
        ),
    )

    val hasMoreAndCursorExists inline get() = hasMore && cursor != null
}
