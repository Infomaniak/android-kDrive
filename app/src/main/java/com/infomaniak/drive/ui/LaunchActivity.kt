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
import com.infomaniak.drive.utils.MatomoUtils.trackCurrentUserId
import com.infomaniak.drive.utils.MatomoUtils.trackScreen
import com.infomaniak.drive.utils.isKeyguardSecure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {

            logoutCurrentUserIfNeeded() // Rights v2 migration temporary fix

            val clazz = when {
                AccountUtils.requestCurrentUser() == null -> {
                    if (getOldkDriveUser().isEmpty) LoginActivity::class.java else MigrationActivity::class.java
                }
                isKeyguardSecure() && AppSettings.appSecurityLock -> {
                    LockActivity::class.java
                }
                else -> {
                    if (DriveInfosController.getDrivesCount(AccountUtils.currentUserId) == 0L) {
                        AccountUtils.updateCurrentUserAndDrives(this@LaunchActivity)
                    }
                    application.trackCurrentUserId()
                    if (DriveInfosController.getDrives(AccountUtils.currentUserId).all { it.maintenance }) {
                        MaintenanceActivity::class.java
                    } else {
                        MainActivity::class.java
                    }
                }
            }
            startActivity(Intent(this@LaunchActivity, clazz))
        }
        trackScreen()
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
