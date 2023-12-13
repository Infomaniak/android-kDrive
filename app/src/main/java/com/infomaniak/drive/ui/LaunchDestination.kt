/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LaunchDestination {

    suspend fun startApp(context: Context, destinationClassArgs: Bundle?) {
        val destinationClass = getDestinationClass(context)

        if (destinationClass == LockActivity::class.java) {
            LockActivity.startAppLockActivity(
                context = context,
                destinationClass = MainActivity::class.java,
                destinationClassArgs = destinationClassArgs
            )
        } else {
            val intent = Intent(context, destinationClass).apply {
                if (destinationClass == MainActivity::class.java) destinationClassArgs?.let(::putExtras)
            }

            context.startActivity(intent)
        }
    }

    private suspend fun getDestinationClass(context: Context): Class<out AppCompatActivity> = withContext(Dispatchers.IO) {
        if (AccountUtils.requestCurrentUser() == null) {
            LoginActivity::class.java
        } else {
            loggedUserDestination(context)
        }
    }

    private suspend fun loggedUserDestination(context: Context): Class<out AppCompatActivity> {
        context.trackUserId(AccountUtils.currentUserId)

        // When DriveInfosController is migrated
        if (DriveInfosController.getDrivesCount(userId = AccountUtils.currentUserId) == 0L) {
            AccountUtils.updateCurrentUserAndDrives(context)
        }

        return when {
            areAllDrivesInMaintenance() -> MaintenanceActivity::class.java
            context.isKeyguardSecure() && AppSettings.appSecurityLock -> LockActivity::class.java
            else -> MainActivity::class.java
        }
    }

    private fun areAllDrivesInMaintenance(): Boolean {
        return DriveInfosController.getDrives(userId = AccountUtils.currentUserId).all { it.maintenance }
    }

}
