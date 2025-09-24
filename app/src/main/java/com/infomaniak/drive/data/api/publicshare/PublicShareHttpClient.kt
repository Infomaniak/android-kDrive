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

import com.infomaniak.core.network.networking.HttpClient.addCache
import com.infomaniak.core.network.networking.HttpClient.addCommonInterceptors
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import okhttp3.OkHttpClient

object PublicShareHttpClient {

    private var tokenInterceptorListener: TokenInterceptorListener? = null

    fun init(tokenInterceptorListener: TokenInterceptorListener) {
        this.tokenInterceptorListener = tokenInterceptorListener
    }

    val publicShareHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().apply {
            addCache()
            addTokenInterceptorIfConnected()
            addCommonInterceptors()
        }.build()
    }

    private fun OkHttpClient.Builder.addTokenInterceptorIfConnected() {
        AccountUtils.currentUser?.let {
            tokenInterceptorListener?.let { listener -> addInterceptor(PublicShareTokenInterceptor(listener)) }
        }
    }
}
