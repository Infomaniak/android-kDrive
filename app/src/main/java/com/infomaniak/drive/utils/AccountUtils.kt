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
package com.infomaniak.drive.utils

import android.content.Context
import androidx.lifecycle.LiveData
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UISettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.CredentialManager
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.room.UserDatabase
import com.infomaniak.lib.login.ApiToken
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AccountUtils : CredentialManager {

    private lateinit var userDatabase: UserDatabase
    var reloadApp: (() -> Unit)? = null

    fun init(context: Context) {
        userDatabase = UserDatabase.getDatabase(context)
        Sentry.setUser(io.sentry.protocol.User().apply { id = currentUserId.toString() })
    }

    var currentUserId: Int = AppSettings.getAppSettings()._currentUserId
        set(userId) {
            field = userId
            GlobalScope.launch(Dispatchers.IO) {
                AppSettings.updateAppSettings { appSettings -> appSettings._currentUserId = userId }
            }
        }

    var currentDriveId: Int = AppSettings.getAppSettings()._currentDriveId
        set(driveId) {
            field = driveId
            GlobalScope.launch(Dispatchers.IO) {
                AppSettings.updateAppSettings { appSettings -> appSettings._currentDriveId = driveId }
            }
        }

    var currentUser: User? = null
        set(user) {
            field = user
            currentUserId = user?.id ?: -1
            getCurrentDrive()
            Sentry.setUser(io.sentry.protocol.User().apply {
                id = currentUserId.toString()
                email = user?.email
            })
            InfomaniakCore.bearerToken = user?.apiToken?.accessToken.toString()
        }

    private var currentDrive: Drive? = null

    suspend fun requestCurrentUser(): User? {
        currentUser = getUserById(currentUserId)
        if (currentUser == null) {
            currentUser = userDatabase.userDao().getFirst()
        }
        return currentUser
    }

    suspend fun addUser(user: User) {
        currentDriveId = -1
        currentUser = user
        userDatabase.userDao().insert(user)
    }

    suspend fun updateCurrentUserAndDrives(
        context: Context,
        fromMaintenance: Boolean = false,
        fromCloudStorage: Boolean = false,
        okHttpClient: OkHttpClient = HttpClient.okHttpClient
    ) = withContext(Dispatchers.IO) {
        val userProfile = ApiRepository.getUserProfile(okHttpClient)

        if (userProfile.result != ApiResponse.Status.ERROR) {
            userProfile.data?.let { user ->
                ApiRepository.getAllDrivesData(okHttpClient).apply {
                    if (result != ApiResponse.Status.ERROR) {
                        data?.let { driveInfo ->
                            val driveRemovedList = DriveInfosController.storeDriveInfos(user.id, driveInfo)
                            val appSyncSettings = UploadFile.getAppSyncSettings()
                            for (driveRemoved in driveRemovedList) {
                                if (appSyncSettings?.userId == user.id && appSyncSettings.driveId == driveRemoved.id) {
                                    Sentry.captureMessage("AccountUtils: disableAutoSync")
                                    context.disableAutoSync()
                                }
                                if (currentDriveId == driveRemoved.id) {
                                    getFirstDrive()
                                    GlobalScope.launch(Dispatchers.Main) {
                                        reloadApp?.invoke()
                                    }
                                }
                                FileController.deleteUserDriveFiles(user.id, driveRemoved.id)
                            }

                            if (fromMaintenance) {
                                if (driveInfo.drives.main.any { drive -> !drive.maintenance }) {
                                    GlobalScope.launch(Dispatchers.Main) {
                                        reloadApp?.invoke()
                                    }
                                }
                            } else if (driveInfo.drives.main.all { drive -> drive.maintenance } ||
                                driveInfo.drives.main.any { drive -> drive.maintenance && drive.id == currentDriveId }) {
                                GlobalScope.launch(Dispatchers.Main) {
                                    reloadApp?.invoke()
                                }
                            }

                            MqttClientWrapper.registerForNotifications(driveInfo.ipsToken)

                            TokenAuthenticator.mutex.withLock {
                                if (currentUserId == user.id) {
                                    user.apply {
                                        organizations = arrayListOf()
                                        requestCurrentUser()?.let { user ->
                                            setUserToken(this, user.apiToken)
                                            currentUser = this
                                        }
                                    }
                                }
                            }
                            if (!fromCloudStorage) {
                                CloudStorageProvider.notifyRootsChanged(context)
                            }
                        }
                    } else if (error?.code?.equals("no_drive") == true) {
                        removeUser(context, user)
                    }
                }
            }
        }
    }

    suspend fun removeUser(context: Context, userRemoved: User) {
        userDatabase.userDao().delete(userRemoved)
        FileController.deleteUserDriveFiles(userRemoved.id)

        if (UploadFile.getAppSyncSettings()?.userId == userRemoved.id) {
            Sentry.captureMessage("AccountUtils: disableAutoSync")
            context.disableAutoSync()
        }

        if (currentUserId == userRemoved.id) {
            requestCurrentUser()
            currentDriveId = -1

            resetApp(context)
            GlobalScope.launch(Dispatchers.Main) {
                reloadApp?.invoke()
            }

            CloudStorageProvider.notifyRootsChanged(context)
        }
    }

    override fun getAllUsers(): LiveData<List<User>> {
        return userDatabase.userDao().getAll()
    }

    fun getAllUsersSync() = userDatabase.userDao().getAllSync()

    suspend fun setUserToken(user: User?, apiToken: ApiToken) {
        user?.let {
            it.apiToken = apiToken
            userDatabase.userDao().update(it)
        }
    }

    suspend fun getHttpClientUser(userID: Int, timeout: Long?, onRefreshTokenError: (user: User) -> Unit): OkHttpClient {
        var user = getUserById(userID)
        return OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(StethoInterceptor())
            }
            timeout?.let {
                callTimeout(timeout, TimeUnit.SECONDS)
                readTimeout(timeout, TimeUnit.SECONDS)
                writeTimeout(timeout, TimeUnit.SECONDS)
                connectTimeout(timeout, TimeUnit.SECONDS)
            }
            val tokenInterceptorListener = object : TokenInterceptorListener {
                override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
                    setUserToken(user, apiToken)
                    if (currentUserId == userID) {
                        currentUser = user
                    }
                }

                override suspend fun onRefreshTokenError() {
                    user?.let {
                        onRefreshTokenError(it)
                    }
                }

                override suspend fun getApiToken(): ApiToken {
                    user = getUserById(userID)
                    return user?.apiToken!!
                }
            }
            addInterceptor(TokenInterceptor(tokenInterceptorListener))
            authenticator(TokenAuthenticator(tokenInterceptorListener))
        }.run {
            build()
        }
    }

    fun getCurrentDrive(): Drive? {
        if (currentDriveId != currentDrive?.id) {
            currentDrive = DriveInfosController.getDrives(currentUserId, currentDriveId, maintenance = false).firstOrNull()
                ?: getFirstDrive()
        }
        return currentDrive
    }

    private fun getFirstDrive(): Drive? {
        val currentDrive = DriveInfosController.getDrives(currentUserId, maintenance = false).firstOrNull()
        currentDriveId = currentDrive?.id ?: -1
        return currentDrive
    }

    suspend fun getUserById(id: Int): User? {
        return userDatabase.userDao().findById(id)
    }

    private fun resetApp(context: Context) {
        if (getAllUsers().value?.size == 0) {
            AppSettings.removeAppSettings()
            UISettings(context).removeUiSettings()
            if (isEnableAppSync()) {
                Sentry.captureMessage("AccountUtils: disableAutoSync")
                context.disableAutoSync()
            }
        }
    }

    fun isEnableAppSync(): Boolean {
        return UploadFile.getAppSyncSettings() != null
    }
}
