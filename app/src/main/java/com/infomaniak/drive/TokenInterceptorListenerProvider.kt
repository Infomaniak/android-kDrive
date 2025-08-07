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
package com.infomaniak.drive

import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.AccountUtils.getUserById
import com.infomaniak.drive.utils.AccountUtils.setUserToken
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.login.ApiToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

object TokenInterceptorListenerProvider {
    private suspend fun onRefreshTokenSuccessCommon(apiToken: ApiToken, user: User?) = setUserToken(user!!, apiToken)
    private suspend fun onRefreshTokenErrorCommon(refreshTokenError: (User) -> Unit, user: User?) = refreshTokenError(user!!)

    fun tokenInterceptorListener(
        refreshTokenError: (User) -> Unit,
        globalCoroutineScope: CoroutineScope,
    ): TokenInterceptorListener = object : TokenInterceptorListener {
        val userTokenFlow by lazy { AppSettings.currentUserIdFlow.mapToApiToken(globalCoroutineScope) }
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            onRefreshTokenSuccessCommon(apiToken, AccountUtils.currentUser)
        }

        override suspend fun onRefreshTokenError() {
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
            onRefreshTokenErrorCommon(refreshTokenError, AccountUtils.currentUser!!)
        }

        override suspend fun getUserApiToken(): ApiToken? = userTokenFlow.first()

        override fun getCurrentUserId(): Int = AccountUtils.currentUserId
    }

    fun tokenInterceptorListenerByUserId(
        refreshTokenError: (User) -> Unit,
        userId: Int,
    ): TokenInterceptorListener = object : TokenInterceptorListener {
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            val user = getUserById(userId)
            onRefreshTokenSuccessCommon(apiToken, user)
        }

        override suspend fun onRefreshTokenError() {
            val user = getUserById(userId)
            onRefreshTokenErrorCommon(refreshTokenError, user)
        }

        override suspend fun getUserApiToken(): ApiToken? {
            val user = getUserById(userId)
            return user?.apiToken
        }

        override fun getCurrentUserId(): Int = userId
    }


}
