/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import android.os.Bundle
import androidx.core.os.bundleOf
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.CredentialManager
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.room.UserDatabase
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.stores.StoresSettingsRepository
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

object AccountUtils : CredentialManager() {

    private const val DISABLE_AUTO_SYNC = "AccountUtils: disableAutoSync"

    override lateinit var userDatabase: UserDatabase
    var reloadApp: ((bundle: Bundle) -> Unit)? = null

    fun init(context: Context) {
        userDatabase = UserDatabase.getDatabase(context)
        Sentry.setUser(io.sentry.protocol.User().apply { id = currentUserId.toString() })
    }

    override var currentUserId: Int = AppSettings.getAppSettings()._currentUserId
        set(userId) {
            field = userId
            GlobalScope.launch(Dispatchers.IO) {
                AppSettings.updateAppSettings { appSettings -> if (appSettings.isValid) appSettings._currentUserId = userId }
            }
        }

    var currentDriveId: Int = AppSettings.getAppSettings()._currentDriveId
        set(driveId) {
            field = driveId
            GlobalScope.launch(Dispatchers.IO) {
                AppSettings.updateAppSettings { appSettings -> if (appSettings.isValid) appSettings._currentDriveId = driveId }
            }
        }

    override var currentUser: User? = null
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
        okHttpClient: OkHttpClient = HttpClient.okHttpClient,
    ) = withContext(Dispatchers.IO) {

        val (userResult, user) = with(ApiRepository.getUserProfile(okHttpClient)) {
            result to (data ?: return@withContext)
        }

        if (userResult != ApiResponseStatus.ERROR) {
            ApiRepository.getAllDrivesData(okHttpClient).apply {
                if (result != ApiResponseStatus.ERROR) {
                    handleDrivesData(context, fromMaintenance, fromCloudStorage, user, data as DriveInfo)
                } else if (error?.code == ErrorCode.NO_DRIVE) {
                    removeUserAndDeleteToken(context, user)
                }
            }
        }
    }

    private suspend fun handleDrivesData(
        context: Context,
        fromMaintenance: Boolean,
        fromCloudStorage: Boolean,
        user: User,
        driveInfo: DriveInfo,
    ) {
        deleteFiles(user, driveInfo, context)
        reloadAppIfNeeded(fromMaintenance, driveInfo)
        MqttClientWrapper.updateToken(driveInfo.ipsToken)
        requestUser(user)
        if (!fromCloudStorage) CloudStorageProvider.notifyRootsChanged(context)
    }

    private fun deleteFiles(user: User, driveInfo: DriveInfo, context: Context) {

        val driveRemovedList = DriveInfosController.storeDriveInfos(user.id, driveInfo)
        val appSyncSettings = UploadFile.getAppSyncSettings()

        for (driveRemoved in driveRemovedList) {
            if (appSyncSettings?.userId == user.id && appSyncSettings.driveId == driveRemoved.id) {
                Sentry.captureMessage(DISABLE_AUTO_SYNC)
                context.disableAutoSync()
            }
            if (currentDriveId == driveRemoved.id) {
                getFirstDrive()
                GlobalScope.launch(Dispatchers.Main) { reloadApp?.invoke(bundleOf()) }
            }
            FileController.deleteUserDriveFiles(user.id, driveRemoved.id)
        }
    }

    private fun reloadAppIfNeeded(fromMaintenance: Boolean, driveInfo: DriveInfo) {
        if (fromMaintenance) {
            if (driveInfo.drives.main.any { drive -> !drive.maintenance }) {
                GlobalScope.launch(Dispatchers.Main) {
                    reloadApp?.invoke(bundleOf())
                }
            }
        } else if (driveInfo.drives.main.all { drive -> drive.maintenance } ||
            driveInfo.drives.main.any { drive -> drive.maintenance && drive.id == currentDriveId }) {
            GlobalScope.launch(Dispatchers.Main) {
                reloadApp?.invoke(bundleOf())
            }
        }
    }

    private suspend fun requestUser(remoteUser: User) {
        TokenAuthenticator.mutex.withLock {
            if (remoteUser.id == currentUserId) {
                remoteUser.organizations = arrayListOf()
                requestCurrentUser()?.let { localUser ->
                    setUserToken(remoteUser, localUser.apiToken)
                }
            }
        }
    }

    suspend fun removeUserAndDeleteToken(context: Context, user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            context.getInfomaniakLogin().deleteToken(
                HttpClient.okHttpClientNoTokenInterceptor,
                user.apiToken,
                onError = {
                    SentryLog.e("deleteTokenError", "Api response error : ${LoginActivity.getLoginErrorDescription(context, it)}")
                }
            )
        }

        removeUser(context, user)
    }

    suspend fun removeUser(context: Context, user: User) {
        userDatabase.userDao().delete(user)
        FileController.deleteUserDriveFiles(user.id)

        if (UploadFile.getAppSyncSettings()?.userId == user.id) {
            Sentry.captureMessage(DISABLE_AUTO_SYNC)
            context.disableAutoSync()
        }

        if (currentUserId == user.id) {
            requestCurrentUser()
            currentDriveId = -1

            resetApp(context)
            GlobalScope.launch(Dispatchers.Main) {
                reloadApp?.invoke(bundleOf())
            }

            CloudStorageProvider.notifyRootsChanged(context)
        }
    }

    fun getAllUsersSync(): List<User> = userDatabase.userDao().getAllSync()

    fun getCurrentDrive(): Drive? {
        if (currentDriveId != currentDrive?.id) {
            currentDrive = DriveInfosController.getDrive(currentUserId, currentDriveId, maintenance = false) ?: getFirstDrive()
        }
        return currentDrive
    }

    private fun getFirstDrive(): Drive? {
        val currentDrive = DriveInfosController.getDrive(currentUserId, sharedWithMe = false, maintenance = false)
        currentDrive?.let { currentDriveId = it.id }
        return currentDrive
    }

    private suspend fun resetApp(context: Context) {
        if (getAllUsersCount() == 0) {
            AppSettings.removeAppSettings()
            UiSettings(context).removeUiSettings()
            StoresSettingsRepository(context).clear()

            if (isEnableAppSync()) {
                Sentry.captureMessage(DISABLE_AUTO_SYNC)
                context.disableAutoSync()
            }

            // Delete all app data
            with(context) {
                filesDir.deleteRecursively()
                cacheDir.deleteRecursively()
            }
            SentryLog.i("AccountUtils", "resetApp> all user data has been deleted")
        }
    }

    fun isEnableAppSync(): Boolean = UploadFile.getAppSyncSettings() != null
}
