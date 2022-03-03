/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.test.runner.permission.PermissionRequester
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.AccountUtils.addUser
import com.infomaniak.drive.utils.AccountUtils.currentUser
import com.infomaniak.drive.utils.AccountUtils.getUserById
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponseData
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.login.ApiToken
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

open class KDriveTest {

    companion object {

        internal const val APP_PACKAGE = BuildConfig.APPLICATION_ID
        internal val context = ApplicationProvider.getApplicationContext<Context>()
        internal lateinit var okHttpClient: OkHttpClient
        internal lateinit var uiRealm: Realm
        internal lateinit var userDrive: UserDrive
        private lateinit var user: User

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            if (Env.USE_CURRENT_USER) {
                user = runBlocking(Dispatchers.IO) { AccountUtils.requestCurrentUser() }!!
                InfomaniakCore.bearerToken = user.apiToken.accessToken

            } else {
                InfomaniakCore.bearerToken = Env.TOKEN

                val apiResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)
                assertApiResponseData(apiResponse)
                user = apiResponse.data!!
                user.apiToken = ApiToken(Env.TOKEN, "", "Bearer", userId = user.id, expiresAt = null)
                runBlocking {
                    if (getUserById(user.id) == null) {
                        user.organizations = arrayListOf()
                        addUser(user)
                    } else {
                        currentUser = user
                    }
                }
            }
            userDrive = UserDrive(user.id, Env.DRIVE_ID)
            okHttpClient = runBlocking { KDriveHttpClient.getHttpClient(user.id) }
            uiRealm = FileController.getRealmInstance(userDrive)

            grantPermissions(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_MEDIA_LOCATION,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.FOREGROUND_SERVICE,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_SYNC_SETTINGS,
                android.Manifest.permission.READ_SYNC_STATS,
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
                android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.Manifest.permission.USE_BIOMETRIC,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.WRITE_SYNC_SETTINGS,
            )
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            ApiRepository.emptyTrash(userDrive.driveId)
            if (!uiRealm.isClosed) uiRealm.close()
            if (!Env.USE_CURRENT_USER) {
                runBlocking { AccountUtils.removeUser(context, user) }
            }
        }

        internal fun getConfig() = RealmConfiguration.Builder().inMemory()
            .name("KDrive-test.realm")
            .deleteRealmIfMigrationNeeded()
            .modules(RealmModules.LocalFilesModule())
            .build()

        private fun grantPermissions(vararg permissions: String) {
            PermissionRequester().apply {
                addPermissions(*permissions)
                requestPermissions()
            }
        }
    }
}
