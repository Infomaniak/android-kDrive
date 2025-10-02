/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.Manifest
import android.os.Build.VERSION.SDK_INT
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.infomaniak.core.legacy.utils.hasPermissions
import com.infomaniak.drive.utils.DrivePermissions.Companion.resultPermissions

class NotificationPermission {

    private lateinit var registerForActivityResult: ActivityResultLauncher<Array<String>>
    private lateinit var activity: FragmentActivity

    fun registerPermission(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        activity = fragment.requireActivity()
        registerForActivityResult =
            fragment.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
                val authorized = authorizedPermissions.values.all { it }
                onPermissionResult?.invoke(authorized)
                activity.resultPermissions(authorized, notificationPermission)
            }
    }

    fun checkNotificationPermission(requestPermission: Boolean = true): Boolean {
        return when {
            SDK_INT < 33 -> true
            activity.hasPermissions(notificationPermission) -> true
            else -> {
                if (requestPermission) registerForActivityResult.launch(notificationPermission)
                false
            }
        }
    }

    companion object {
        private val notificationPermission = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    }
}
