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
package com.infomaniak.drive.fcm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.infomaniak.core.auth.BuildConfig.INFOMANIAK_API_V1
import com.infomaniak.core.cancellable
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.networking.ManualAuthorizationRequired
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerializationException
import operations.OperationHandler
import operations.httpLogging
import operations.plus
import operations.runOperation
import operations.then
import java.io.IOException

class RegisterForNotificationsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val allUsers = AccountUtils.allUsers()
        val token = Firebase.messaging.token.await()
        val registrationInfo = RegistrationInfo(token = token)
        val results = coroutineScope { allUsers.map { user -> async { sendTokenForUser(user, registrationInfo) } }.awaitAll() }
        if (results.any { it == Outcome.ShouldRetry }) {
            Result.retry()
        } else Result.success()
    }.cancellable().getOrElse {
        if (it is IOException) Result.retry() else Result.failure()
    }

    private val registerHttpHandler = OperationHandler.httpLogging(
        tag = TAG,
        operationName = "Register for notifications",
    ) + OperationHandler<HttpResponse, Outcome>(
        onException = { _ -> Outcome.ShouldRetry },
        onResponse = { response -> if (response.status.value in 500..599) Outcome.ShouldRetry else Outcome.Done }
    )










    private suspend fun sendTokenForUser(user: User, registrationInfo: RegistrationInfo): Outcome {
        val httpClient = httpClientForUser(user)
        val response = runCatching {
            SentryLog.i(TAG, "Getting the")
            httpClient.post("devices/register") {
                setBody(registrationInfo)
            }
        }.cancellable().getOrElse { t ->
            if (t is IOException) {
                SentryLog.i(TAG, "", t)
            } else {
                SentryLog.e(TAG, "", t)
            }
            return Outcome.ShouldRetry
        }
        return if (response.status.isSuccess()) {
            SentryLog.i(TAG, "User ${user.id} is registered for notifications")
            Outcome.Done
        } else {
            val httpStatusCode = response.status.value
            val errorMessage = "sendTokenForUser led to http $httpStatusCode"
            when (httpStatusCode) {
                in 500..599 -> {
                    SentryLog.i(TAG, errorMessage)
                    Outcome.ShouldRetry
                }
                401 -> {
                    SentryLog.w(TAG, errorMessage)
                    Outcome.Done
                }
                else -> {
                    SentryLog.wtf(TAG, errorMessage)
                    Outcome.Done
                }
            }
        }
    }





















    private suspend fun sendTokenForUser2(user: User, registrationInfo: RegistrationInfo): Outcome {
        val httpClient = httpClientForUser(user)
        val registerHttpHandler = OperationHandler.httpLogging(
            tag = TAG,
            operationName = "Register for notifications",
        ) then OperationHandler(
            onException = { _ -> Outcome.ShouldRetry },
            onResponse = { response -> if (response.status.value in 500..599) Outcome.ShouldRetry else Outcome.Done }
        )
        return runOperation(registerHttpHandler) {
            httpClient.post("devices/register") {
                setBody(registrationInfo)
            }
        }
    }























    private suspend fun httpClientForUser(user: User): HttpClient {
        val okHttpClient = AccountUtils.getHttpClient(userId = user.id)
        return HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json()
            }
            install(HttpRequestRetry) {
                maxRetries = 3
                retryOnExceptionIf { request, cause -> cause !is SerializationException }
            }
            defaultRequest {
                userAgent(HttpUtils.getUserAgent)
                headers {
                    @OptIn(ManualAuthorizationRequired::class) // Already handled by the http client.
                    HttpUtils.getHeaders().forEach { (header, value) -> append(header, value) }
                }
                url("$INFOMANIAK_API_V1/")
            }
        }
    }

    private enum class Outcome {
        Done,
        ShouldRetry,
    }

    private companion object {
        private const val TAG = "RegisterForNotificationsWorker"
    }
}
