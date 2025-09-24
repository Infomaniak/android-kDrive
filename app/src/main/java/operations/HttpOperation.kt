/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package operations

import com.infomaniak.core.sentry.SentryLog
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException

/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

class LogLevels(
    val start: LogLevel? = LogLevel.I,
    val success: LogLevel? = LogLevel.I,
    val unauthorized: LogLevel? = LogLevel.W,
    val backendError: LogLevel? = LogLevel.I,
    val requestIssue: LogLevel? = LogLevel.Wtf,
    val wrongContentType: LogLevel? = LogLevel.Wtf,
    val ioException: LogLevel? = LogLevel.I,
    val otherException: LogLevel? = LogLevel.Wtf,
    val cancellation: LogLevel? = LogLevel.I,
)

fun OperationHandler.Companion.httpLogging(
    tag: String,
    operationName: String,
    logLevels: LogLevels = LogLevels(),
    expectedContentType: ContentType? = ContentType.Application.Json,
): OperationHandler<HttpResponse, Unit> {
    return OperationHandler(
        onStart = { log(logLevels.start, tag, "$operationName starting") },
        onCancellation = { log(logLevels.cancellation, tag, "$operationName was cancelled") },
        onException = { t ->
            if (t is IOException) log(logLevels.ioException, tag, "$operationName failed", t)
            else log(logLevels.otherException, tag, "$operationName failed", t)
        },
        onResponse = { response ->
            logResponse(tag, operationName, response, logLevels, expectedContentType)
        }
    )
}

private fun logResponse(
    tag: String,
    operationName: String,
    response: HttpResponse,
    logLevels: LogLevels,
    expectedContentType: ContentType?
) {
    val httpStatusCode = response.status.value
    val logLevel: LogLevel?
    val message = if (response.status.isSuccess()) {
        if (response.hasExpectedContentType(expectedContentType)) {
            logLevel = logLevels.success
            "$operationName succeeded"
        } else {
            logLevel = logLevels.wrongContentType
            "$operationName led to http $httpStatusCode with the wrong ContentType. " +
                    "Expected $expectedContentType but got ${response.contentType()}"
        }
    } else {
        logLevel = when (httpStatusCode) {
            in 500..599 -> logLevels.backendError
            401 -> logLevels.unauthorized
            else -> logLevels.requestIssue
        }
        "$operationName led to http $httpStatusCode"
    }
    log(level = logLevel, tag = tag, msg = message)
}

private fun log(level: LogLevel?, tag: String, msg: String, t: Throwable? = null) {
    when (level) {
        null -> Unit
        LogLevel.V -> SentryLog.v(tag = tag, msg = msg, throwable = t)
        LogLevel.D -> SentryLog.d(tag = tag, msg = msg, throwable = t)
        LogLevel.I -> SentryLog.i(tag = tag, msg = msg, throwable = t)
        LogLevel.W -> SentryLog.w(tag = tag, msg = msg, throwable = t)
        LogLevel.E -> SentryLog.e(tag = tag, msg = msg, throwable = t)
        LogLevel.Wtf -> SentryLog.wtf(tag = tag, msg = msg, throwable = t)
    }
}
