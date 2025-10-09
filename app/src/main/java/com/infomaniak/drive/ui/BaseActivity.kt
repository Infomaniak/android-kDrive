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

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.core.extensions.isNightModeEnabled
import com.infomaniak.core.extensions.lightStatusBar
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.runBlocking

open class BaseActivity : EdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.lightStatusBar(!isNightModeEnabled())

        if (AccountUtils.currentUser == null) {
            runBlocking { AccountUtils.requestCurrentUser() }
            trackUserId(AccountUtils.currentUserId)
        }
        trackScreen()
    }
}

abstract class EdgeToEdgeActivity(): AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }
}
