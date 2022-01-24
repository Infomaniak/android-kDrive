/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.login.ApiToken
import io.realm.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass

open class KDriveTest {
    companion object {
        internal val context = ApplicationProvider.getApplicationContext<Context>()
        internal lateinit var user: User
        internal lateinit var userDrive: UserDrive
        internal lateinit var okHttpClient: OkHttpClient

        @BeforeClass
        @JvmStatic
        fun beforeAll() {
            if (Env.USE_CURRENT_USER) {
                user = runBlocking(Dispatchers.IO) { AccountUtils.requestCurrentUser() }!!
                InfomaniakCore.bearerToken = user.apiToken.accessToken

            } else {
                InfomaniakCore.bearerToken = Env.TOKEN

                val apiResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)
                user = apiResponse.data!!
                runBlocking {
                    user.apiToken = ApiToken(Env.TOKEN, "", "Bearer", userId = user.id, expiresAt = null)
                    user.organizations = arrayListOf()
                    AccountUtils.addUser(user)
                }
            }
            userDrive = UserDrive(user.id, Env.DRIVE_ID)
            okHttpClient = runBlocking { KDriveHttpClient.getHttpClient(user.id) }
        }

        @AfterClass
        @JvmStatic
        fun afterAll() {
            if (!Env.USE_CURRENT_USER) {
                runBlocking { AccountUtils.removeUser(context, user) }
            }
        }

        internal fun getConfig() = RealmConfiguration.Builder().inMemory()
            .name("KDrive-test.realm")
            .deleteRealmIfMigrationNeeded()
            .modules(RealmModules.LocalFilesModule())
            .build()
    }
}