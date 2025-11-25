/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import com.infomaniak.core.legacy.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.legacy.models.user.User
import com.infomaniak.core.legacy.networking.HttpClient
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.AccountUtils.addUser
import com.infomaniak.drive.utils.AccountUtils.getUserById
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponseData
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.lib.login.ApiToken
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.exceptions.RealmFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

open class KDriveTest {

    companion object {

        internal const val APP_PACKAGE = BuildConfig.APPLICATION_ID
        internal val context = ApplicationProvider.getApplicationContext<Context>()
        internal lateinit var okHttpClient: OkHttpClient
        internal lateinit var uiRealm: Realm
        internal lateinit var user: User
        internal lateinit var userDrive: UserDrive

        @BeforeAll
        @JvmStatic
        fun beforeAll() = runTest {
            if (Env.USE_CURRENT_USER) {
                user = runBlocking(Dispatchers.IO) { AccountUtils.requestCurrentUser() }!!
            } else {
                val apiToken = ApiToken(
                    accessToken = Env.TOKEN,
                    refreshToken = null,
                    tokenType = "Bearer",
                    userId = -1,
                    expiresAt = null,
                )

                val okhttpClient = HttpClient.okHttpClientNoTokenInterceptor.newBuilder().addInterceptor { chain ->
                    val newRequest = changeAccessToken(chain.request(), apiToken)
                    chain.proceed(newRequest)
                }.build()

                val apiResponse = ApiRepository.getUserProfile(okhttpClient)
                assertApiResponseData(apiResponse)
                user = apiResponse.data!!
                user.apiToken = ApiToken(Env.TOKEN, "", "Bearer", userId = user.id, expiresAt = null)

                runBlocking {
                    if (getUserById(user.id) == null) {
                        user.organizations = arrayListOf()
                        addUser(user)
                    } else {
                        AccountUtils.currentUser = user
                    }
                }
            }

            userDrive = UserDrive(user.id, Env.DRIVE_ID)
            okHttpClient = runBlocking { AccountUtils.getHttpClient(user.id) }

            setUpRealm()

            grantPermissions()
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            ApiRepository.emptyTrash(userDrive.driveId)
            if (!uiRealm.isClosed) uiRealm.close()
            Realm.deleteRealm(FileController.getRealmConfiguration(userDrive))
            if (!Env.USE_CURRENT_USER) {
                runBlocking { AccountUtils.removeUser(context, user) }
            }
        }

        internal fun getRealmConfigurationTest() = RealmConfiguration.Builder().inMemory()
            .name("KDrive-test.realm")
            .deleteRealmIfMigrationNeeded()
            .modules(RealmModules.LocalFilesModule())
            .build()

        private fun setUpRealm() {
            try {
                uiRealm = FileController.getRealmInstance(userDrive)
            } catch (realmFileException: RealmFileException) {
                IOFile(FileController.getRealmConfiguration(userDrive).path).apply {
                    if (exists()) {
                        delete()
                        uiRealm = FileController.getRealmInstance(userDrive)
                    } else {
                        realmFileException.printStackTrace()
                        assert(false) { "realmFileException thrown" }
                        throw realmFileException
                    }
                }
            }
        }

        private fun grantPermissions() {
            PermissionRequester().apply {

                val permissions = DrivePermissions.permissionsFor(
                    type = DrivePermissions.Type.ReadingMediaForSync,
                    includeOptionals = true,
                ).toTypedArray()

                addPermissions(*permissions)
                requestPermissions()
            }
        }
    }
}
