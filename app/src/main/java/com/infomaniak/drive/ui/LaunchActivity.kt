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

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileMigration
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.ui.login.MigrationActivity
import com.infomaniak.drive.ui.login.MigrationActivity.Companion.getOldkDriveUser
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.isKeyguardSecure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matomo.sdk.extra.MatomoApplication
import org.matomo.sdk.extra.TrackHelper

class LaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {

            logoutCurrentUserIfNeeded() // Rights v2 migration temporary fix

            when {
                AccountUtils.requestCurrentUser() == null -> {
                    if (getOldkDriveUser().isEmpty) {
                        startActivity(Intent(this@LaunchActivity, LoginActivity::class.java))
                    } else {
                        startActivity(Intent(this@LaunchActivity, MigrationActivity::class.java))
                    }
                }
                isKeyguardSecure() && AppSettings.appSecurityLock -> {
                    startActivity(Intent(this@LaunchActivity, LockActivity::class.java))
                }
                else -> {
                    if (DriveInfosController.getDrivesCount(AccountUtils.currentUserId) == 0L) {
                        AccountUtils.updateCurrentUserAndDrives(this@LaunchActivity)
                    }
                    if (DriveInfosController.getDrives(AccountUtils.currentUserId).all { it.maintenance }) {
                        startActivity(Intent(this@LaunchActivity, MaintenanceActivity::class.java))
                    } else {
                        startActivity(Intent(this@LaunchActivity, MainActivity::class.java))
                    }
                }
            }
        }
        (application as MatomoApplication).tracker.apply {
            userId = AccountUtils.currentUserId.toString()
            TrackHelper.track().screen("/LaunchActivity").title("Launch").with(this)
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private suspend fun logoutCurrentUserIfNeeded() = withContext(Dispatchers.IO) {
        intent.extras?.getBoolean(FileMigration.LOGOUT_CURRENT_USER_TAG)?.let { needLogoutCurrentUser ->
            if (needLogoutCurrentUser) {
                if (AccountUtils.currentUser == null) AccountUtils.requestCurrentUser()
                AccountUtils.currentUser?.let { AccountUtils.removeUser(this@LaunchActivity, it) }
            }
        }
    }
}
