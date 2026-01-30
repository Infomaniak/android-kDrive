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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.auth.room.UserDatabase
import com.infomaniak.core.legacy.extensions.setDefaultLocaleIfNeeded
import com.infomaniak.core.legacy.utils.showToast
import com.infomaniak.core.network.models.ApiError
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.ui.view.edgetoedge.EdgeToEdgeActivity
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackDeepLink
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.publicshare.PublicShareApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileMigration
import com.infomaniak.drive.data.models.DeepLinkType
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.ui.login.LoginActivityArgs
import com.infomaniak.drive.ui.publicShare.PublicShareActivity
import com.infomaniak.drive.ui.publicShare.PublicShareActivity.Companion.PUBLIC_SHARE_TAG
import com.infomaniak.drive.ui.publicShare.PublicShareActivityArgs
import com.infomaniak.drive.ui.publicShare.PublicShareListFragment.Companion.PUBLIC_SHARE_DEFAULT_ID
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.PublicShareUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.core.network.models.exceptions.NetworkException as ApiControllerNetworkException

@SuppressLint("CustomSplashScreen")
class LaunchActivity : EdgeToEdgeActivity() {

    private val navigationArgs: LaunchActivityArgs? by lazy { intent?.extras?.let { LaunchActivityArgs.fromBundle(it) } }
    private var mainActivityExtras: Bundle? = null
    private var publicShareActivityExtras: Bundle? = null
    private var isHelpShortcutPressed = false
    private var shouldStartApp = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDefaultLocaleIfNeeded()

        trackScreen()

        lifecycleScope.launch {

            logoutCurrentUserIfNeeded() // Rights v2 migration temporary fix
            handleNotificationDestinationIntent()
            handleShortcuts()
            handleDeeplink()

            if (shouldStartApp) startApp()

            // After starting the destination activity, we run finish to make sure we close the LaunchScreen,
            // so that even when we return, the activity will still be closed.
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        if (SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private suspend fun startApp() {

        val destinationClass = getDestinationClass()

        Intent(this, destinationClass).apply {
            when (destinationClass) {
                MainActivity::class.java -> mainActivityExtras?.let(::putExtras)
                LoginActivity::class.java -> {
                    putExtra("isHelpShortcutPressed", isHelpShortcutPressed)
                    putExtras(LoginActivityArgs(displayOnlyLastPage = false).toBundle())
                }
                PublicShareActivity::class.java -> {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    publicShareActivityExtras?.let(::putExtras)
                }
            }
        }.also(::startActivity)
    }

    private suspend fun getDestinationClass(): Class<out Activity> = withContext(Dispatchers.IO) {
        when {
            publicShareActivityExtras != null -> PublicShareActivity::class.java
            AccountUtils.requestCurrentUser() == null -> LoginActivity::class.java
            else -> loggedUserDestination()
        }
    }

    private suspend fun loggedUserDestination(): Class<out AppCompatActivity> {
        trackUserId(AccountUtils.currentUserId)

        // When DriveInfosController is migrated
        if (DriveInfosController.getDrivesCount(userId = AccountUtils.currentUserId) == 0L) {
            AccountUtils.updateCurrentUserAndDrives(this)
        }

        val areAllDrivesInMaintenance = DriveInfosController.getDrives(userId = AccountUtils.currentUserId)
            .takeUnless { it.isEmpty() }
            ?.all { it.maintenance }
            ?: false

        return if (areAllDrivesInMaintenance) {
            MaintenanceActivity::class.java
        } else {
            MainActivity::class.java
        }
    }

    private suspend fun handleNotificationDestinationIntent() {
        val navArgs = navigationArgs ?: return
        if (navArgs.destinationUserId == 0 || navArgs.destinationDriveId == 0) return
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = UploadWorker.BREADCRUMB_TAG
            message = "Upload notification has been clicked"
            level = SentryLevel.INFO
        })
        if (UserDatabase().userDao().findById(navArgs.destinationUserId) == null) {
            mainActivityExtras = MainActivityArgs(deepLinkFileNotFound = true).toBundle()
        } else {
            Dispatchers.IO {
                DriveInfosController.getDrive(driveId = navArgs.destinationDriveId, maintenance = false)
            }?.also { drive ->
                setOpenSpecificFile(
                    userId = drive.userId,
                    driveId = drive.id,
                    fileId = navArgs.destinationRemoteFolderId,
                    isSharedWithMe = drive.sharedWithMe,
                )
            }
        }
    }

    private suspend fun handleDeeplink() = Dispatchers.IO {
        intent.data?.path?.let { deeplink ->
            // If the app is closed, the currentUser will be null. We don't want that otherwise the link will always be opened as
            // external instead of internal if you already have access to the files. So we set it here
            if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()

            if (deeplink.contains("/app/share/")) processPublicShare(deeplink) else processInternalLink(deeplink)
            SentryLog.i(UploadWorker.BREADCRUMB_TAG, "DeepLink: $deeplink")
        }
    }

    private suspend fun processPublicShare(path: String) {
        Regex("/app/share/(\\d+)/([a-z0-9-]+)").find(path)?.let { match ->
            val (driveId, publicShareUuid) = match.destructured

            val apiResponse = PublicShareApiRepository.getPublicShareInfo(driveId.toInt(), publicShareUuid)
            when (apiResponse.result) {
                ApiResponseStatus.SUCCESS -> {
                    val shareLink = apiResponse.data!!
                    setPublicShareActivityArgs(driveId, publicShareUuid, shareLink)
                }
                ApiResponseStatus.REDIRECT -> apiResponse.uri?.let(::processInternalLink)
                else -> handlePublicShareError(apiResponse.error, driveId, publicShareUuid)
            }
        }
    }

    private suspend fun handlePublicShareError(error: ApiError?, driveId: String, publicShareUuid: String) {
        when {
            error?.exception is ApiControllerNetworkException -> {
                Dispatchers.Main { showToast(R.string.errorNetwork) }
                finishAndRemoveTask()
            }
            error?.code == ErrorCode.PASSWORD_NOT_VALID -> {
                setPublicShareActivityArgs(driveId, publicShareUuid, isPasswordNeeded = true)
            }
            error?.code == ErrorCode.PUBLIC_SHARE_LINK_IS_NOT_VALID -> {
                setPublicShareActivityArgs(driveId, publicShareUuid, isExpired = true)
            }
            else -> SentryLog.e(PUBLIC_SHARE_TAG, "Error during getPublicShareFile: ${error?.code} / ${error?.description}")
        }
    }

    private fun processInternalLink(path: String) {
        Regex("/app/[a-z]+/(\\d+)/([a-z-]*)/?[a-z]*/?[a-z]*/?(\\d*)/?[a-z]*/?[a-z]*/?(\\d*)").find(path)?.let { match ->
            val (pathDriveId, roleFolderId, pathFolderId, pathFileId) = match.destructured

            val driveId = pathDriveId.toInt()
            val fileId = if (pathFileId.isEmpty()) pathFolderId.toIntOrNull() ?: ROOT_ID else pathFileId.toInt()

            when (roleFolderId) {
                SHARED_WITH_ME_FOLDER_ROLE -> {
                    // In case of SharedWithMe deeplinks, we open the link in the web as we cannot support them in-app for now
                    PublicShareUtils.openDeepLinkInBrowser(activity = this, path)
                    shouldStartApp = false
                    return
                }
                TRASH -> {
                    mainActivityExtras = MainActivityArgs(
                        deepLinkType = DeepLinkType.Trash(
                            organizationId = null,
                            userDriveId = driveId,
                            folderId = pathFolderId.takeIf { it.isNotEmpty() }
                        )
                    ).toBundle()
                    return
                }
            }

            lifecycleScope.launch {
                Dispatchers.IO { DriveInfosController.getDrive(driveId = driveId, maintenance = false) }?.also {
                    setOpenSpecificFile(it.userId, driveId, fileId, it.sharedWithMe)
                } ?: run {
                    mainActivityExtras = MainActivityArgs(deepLinkFileNotFound = true).toBundle()
                }
            }

            trackDeepLink(MatomoName.Internal)
        }
    }

    private fun setOpenSpecificFile(userId: Int, driveId: Int, fileId: Int, isSharedWithMe: Boolean) {
        if (userId != AccountUtils.currentUserId) AccountUtils.currentUserId = userId
        if (!isSharedWithMe && driveId != AccountUtils.currentDriveId) AccountUtils.currentDriveId = driveId
        val userDrive = UserDrive(sharedWithMe = isSharedWithMe, driveId = driveId)
        mainActivityExtras = MainActivityArgs(destinationFileId = fileId, destinationUserDrive = userDrive).toBundle()
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
        intent.extras?.getString(SHORTCUTS_TAG)?.let { shortcutTag ->
            mainActivityExtras = MainActivityArgs(shortcutId = shortcutTag).toBundle()
            if (shortcutTag == Utils.Shortcuts.FEEDBACK.id) isHelpShortcutPressed = true
        }
    }

    private fun setPublicShareActivityArgs(
        driveId: String,
        publicShareUuid: String,
        shareLink: ShareLink? = null,
        isPasswordNeeded: Boolean = false,
        isExpired: Boolean = false,
    ) {
        publicShareActivityExtras = PublicShareActivityArgs(
            driveId = driveId.toInt(),
            publicShareUuid = publicShareUuid,
            fileId = shareLink?.fileId ?: PUBLIC_SHARE_DEFAULT_ID,
            isPasswordNeeded = isPasswordNeeded,
            isExpired = isExpired,
            canDownload = shareLink?.capabilities?.canDownload == true,
        ).toBundle()

        val trackerName = when {
            isPasswordNeeded -> MatomoName.PublicShareWithPassword
            isExpired -> MatomoName.PublicShareExpired
            else -> MatomoName.PublicShare
        }

        trackDeepLink(trackerName)
    }

    companion object {
        private const val SHORTCUTS_TAG = "shortcuts_tag"
        private const val SHARED_WITH_ME_FOLDER_ROLE = "shared-with-me"
        private const val TRASH = "trash"
    }
}
