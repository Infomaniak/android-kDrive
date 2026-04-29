/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.deeplink

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.infomaniak.core.webview.ui.WebViewActivity
import com.infomaniak.drive.BuildConfig.DEBUG
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.deeplink.DeeplinkType
import com.infomaniak.drive.data.models.deeplink.DeeplinkType.DeeplinkAction
import com.infomaniak.drive.data.models.deeplink.DeeplinkType.Unmanaged.BrowserLaunch
import com.infomaniak.drive.data.models.deeplink.DeeplinkType.Unmanaged.NotAccessible
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.openOnlyOfficeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch


class DeeplinkHandler(registryOwner: SavedStateRegistryOwner) : SavedStateRegistry.SavedStateProvider {

    private var deeplinkConsumed: Boolean = false

    init {
        registryOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                registryOwner.savedStateRegistry.run {
                    registerSavedStateProvider(PROVIDER, this@DeeplinkHandler)
                    deeplinkConsumed = consumeRestoredStateForKey(PROVIDER)?.getBoolean(HANDLED_DEEPLINK_STATE_KEY) ?: false
                }
            }
        })
    }


    override fun saveState(): Bundle {
        return bundleOf(HANDLED_DEEPLINK_STATE_KEY to deeplinkConsumed)
    }

    fun handle(deeplinkType: DeeplinkType?, activity: MainActivity) {
        deeplinkType.takeUnless { deeplinkConsumed }?.run {
            deeplinkConsumed = true
            when (this) {
                is DeeplinkAction.Collaborate -> handleCollaborateDeeplink()
                is DeeplinkAction.Drive -> handleDriveDeeplink(activity)
                is DeeplinkAction.Office -> handleOnlyOfficeDeeplink(activity)
                is DeeplinkType.Unmanaged -> handleUnmanagedDeeplink(activity)
            }
        }
    }

    private fun DeeplinkAction.Collaborate.handleCollaborateDeeplink() {
        if (DEBUG && isHandled) TODO("Need to implement here when Collaborate deeplink will be supported")
    }

    private fun DeeplinkAction.Drive.handleDriveDeeplink(activity: MainActivity) {
        with(activity) {
            lifecycleScope.launch(context = Dispatchers.IO) {
                DriveInfosController.getDrive(userId = userId, driveId = driveId, maintenance = false)
                    ?.ensureRightUser()
                    ?.let { navigateFromDriveDeeplink(this@handleDriveDeeplink) }
            }
        }
    }

    private fun DeeplinkAction.Office.handleOnlyOfficeDeeplink(activity: MainActivity) {
        with(activity) {
            lifecycleScope.launch(context = Dispatchers.IO) {
                DriveInfosController.getDrive(userId = userId, driveId = driveId, maintenance = false)
                    ?.ensureRightUser()
                    ?.run { FileController.getFileById(fileId = fileId, userDrive = UserDrive(userId = userId, driveId = id)) }
                    ?.let { Dispatchers.Main { openOnlyOfficeActivity(it) } }
            }
        }
    }

    private fun DeeplinkType.Unmanaged.handleUnmanagedDeeplink(activity: MainActivity) {
        when (this) {
            is BrowserLaunch -> activity.startWebViewActivity(url)
            NotAccessible -> activity.showSnackbarWithFabAnchor(R.string.noRightsToOfficeLink)
        }
    }

    private suspend fun Drive.ensureRightUser(): Drive = also {
        if (userId != AccountUtils.currentUserId) {
            AccountUtils.currentUserId = userId
            AccountUtils.requestCurrentUser()
        }
        if (!sharedWithMe && id != AccountUtils.currentDriveId) AccountUtils.currentDriveId = id
    }

    private fun MainActivity.startWebViewActivity(url: String) {
        WebViewActivity.startActivity(
            context = this,
            url = url,
            headers = AccountUtils.currentUser?.run { mapOf("Authorization" to "Bearer ${apiToken.accessToken}") },
            hostWhiteList = setOf("ksuite.infomaniak.com", "kdrive.infomaniak.com"),
        )
    }

    companion object {
        private const val PROVIDER = "DeeplinkHandler"
        private const val HANDLED_DEEPLINK_STATE_KEY = "HANDLED_DEEPLINK_STATE_KEY"
    }
}
