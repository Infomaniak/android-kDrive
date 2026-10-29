/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.models.exceptions.NetworkException
import com.infomaniak.core.sentry.SentryLog
import kotlinx.serialization.SerializationException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Request-scoped diagnostics: never log URLs, bodies, identifiers, headers or exception messages. */
internal class CopyToDriveDiagnostics : Interceptor {

    private var httpStatus: Int? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request()).also { httpStatus = it.code }
    }

    fun report(response: ApiResponse<*>) {
        val outcome = when {
            response.error?.exception is SerializationException -> "decoder_failure"
            response.error?.exception is IOException || response.error?.exception is NetworkException -> "transport_failure"
            httpStatus?.let { it >= 400 } == true -> "http_error"
            response.result == ApiResponseStatus.ERROR -> "api_or_client_error"
            response.result == ApiResponseStatus.SUCCESS || response.result == ApiResponseStatus.ASYNCHRONOUS -> "accepted"
            else -> "unexpected_result"
        }
        val message = "stage=response outcome=$outcome http_status=${httpStatus ?: "none"} result=${response.result.name}"
        if (outcome == "accepted") SentryLog.i(TAG, message) else SentryLog.w(TAG, message)
    }

    companion object {
        const val TAG = "CopyToDrive"
    }
}
