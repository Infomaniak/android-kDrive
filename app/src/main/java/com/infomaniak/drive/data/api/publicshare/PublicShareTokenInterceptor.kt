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
package com.infomaniak.drive.data.api.publicshare

import com.infomaniak.core.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.auth.TokenInterceptorListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class PublicShareTokenInterceptor(
    private val tokenInterceptorListener: TokenInterceptorListener
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        runBlocking(Dispatchers.Default) {
            tokenInterceptorListener.getApiToken()
        }?.let { apiToken ->
            val authorization = request.header("Authorization")
            if (apiToken.accessToken != authorization?.replaceFirst("Bearer ", "")) {
                request = changeAccessToken(request, apiToken)
            }
        }

        return chain.proceed(request)
    }
}
