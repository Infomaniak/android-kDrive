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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileMigration
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.lib.core.extensions.setDefaultLocalIfNeeded
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {

    private val navigationArgs: LaunchActivityArgs? by lazy { intent?.extras?.let { LaunchActivityArgs.fromBundle(it) } }
    private var extrasMainActivity: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDefaultLocalIfNeeded()

        lifecycleScope.launch {

            logoutCurrentUserIfNeeded() // Rights v2 migration temporary fix
            handleNotificationDestinationIntent()
            handleDeeplink()
            handleShortcuts()

            startApp()

            // After starting the destination activity, we run finish to make sure we close the LaunchScreen,
            // so that even when we return, the activity will still be closed.
            finish()
        }
        trackScreen()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private suspend fun startApp() {

        val destinationClass = getDestinationClass()

        if (destinationClass == LockActivity::class.java) {
            LockActivity.startAppLockActivity(
                context = this,
                destinationClass = MainActivity::class.java,
                destinationClassArgs = extrasMainActivity
            )
        } else {
            val intent = Intent(this, destinationClass).apply {
                if (destinationClass == MainActivity::class.java) extrasMainActivity?.let(::putExtras)
            }

            startActivity(intent)
        }
    }

    private suspend fun getDestinationClass(): Class<out AppCompatActivity> = withContext(Dispatchers.IO) {
        if (AccountUtils.requestCurrentUser() == null) {
            LoginActivity::class.java
        } else {
            loggedUserDestination()
        }
    }

    private suspend fun loggedUserDestination(): Class<out AppCompatActivity> {
        trackUserId(AccountUtils.currentUserId)

        // When DriveInfosController is migrated
        if (DriveInfosController.getDrivesCount(userId = AccountUtils.currentUserId) == 0L) {
            AccountUtils.updateCurrentUserAndDrives(this)
        }

        val areAllDrivesInMaintenance = DriveInfosController.getDrives(userId = AccountUtils.currentUserId).all { it.maintenance }

        return when {
            areAllDrivesInMaintenance -> MaintenanceActivity::class.java
            isKeyguardSecure() && AppSettings.appSecurityLock -> LockActivity::class.java
            else -> MainActivity::class.java
        }
    }

    private fun handleNotificationDestinationIntent() {
        navigationArgs?.let {
            if (it.destinationUserId != 0 && it.destinationDriveId != 0) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = UploadWorker.BREADCRUMB_TAG
                    message = "Upload notification has been clicked"
                    level = SentryLevel.INFO
                })
                setOpenSpecificFile(
                    userId = it.destinationUserId,
                    driveId = it.destinationDriveId,
                    fileId = it.destinationRemoteFolderId
                )
            }
        }
    }

    private fun handleDeeplink() {
        intent.data?.let { uri -> uri.path?.let { path -> processDeepLink(path) } }
    }

    private fun processDeepLink(path: String) {
        Regex("/app/[a-z]+/(\\d+)/[a-z]*/?[a-z]*/?[a-z]*/?(\\d*)/?[a-z]*/?[a-z]*/?(\\d*)").find(path)?.let { match ->
            val (pathDriveId, pathFolderId, pathFileId) = match.destructured
            val driveId = pathDriveId.toInt()
            val fileId = if (pathFileId.isEmpty()) pathFolderId.toIntOrNull() ?: ROOT_ID else pathFileId.toInt()

            DriveInfosController.getDrive(driveId = driveId, maintenance = false)?.let {
                setOpenSpecificFile(userId = it.userId, driveId = driveId, fileId = fileId)
            }

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = UploadWorker.BREADCRUMB_TAG
                message = "DeepLink: $path"
                level = SentryLevel.INFO
            })
            trackEvent("deepLink", path)
        }
    }

    private fun setOpenSpecificFile(userId: Int, driveId: Int, fileId: Int) {
        if (userId != AccountUtils.currentUserId) AccountUtils.currentUserId = userId
        if (driveId != AccountUtils.currentDriveId) AccountUtils.currentDriveId = driveId
        extrasMainActivity = MainActivityArgs(destinationFileId = fileId).toBundle()
    }

    private suspend fun logoutCurrentUserIfNeeded() = withContext(Dispatchers.IO) {
        intent.extras?.getBoolean(FileMigration.LOGOUT_CURRENT_USER_TAG)?.let { needLogoutCurrentUser ->
            if (needLogoutCurrentUser) {
                if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
                AccountUtils.currentUser?.let { AccountUtils.removeUserAndDeleteToken(this@LaunchActivity, it) }
            }
        }
    }

    private fun handleShortcuts() {
        intent.extras?.getString(SHORTCUTS_TAG)?.let { extrasMainActivity = MainActivityArgs(shortcutId = it).toBundle() }
    }

    private companion object {
        const val SHORTCUTS_TAG = "shortcuts_tag"
    }
}
