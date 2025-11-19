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

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.infomaniak.core.extensions.isNightModeEnabled
import com.infomaniak.core.extensions.lightStatusBar
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.core.twofactorauth.front.addComposeOverlay
import com.infomaniak.core.ui.view.edgetoedge.EdgeToEdgeActivity
import com.infomaniak.drive.MatomoDrive.trackScreen
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.twoFactorAuthManager
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.runBlocking

open class BaseActivity : EdgeToEdgeActivity() {

    /**
     * Enables the auto-managed 2 factor authentication challenge overlay for View-based Activities.
     *
     * ### 2 important things:
     *
     * 1. **Always call this after [setContentView].**
     * 2. If you need to use it inside a compose-based Activity (i.e. w/ `setContent`), use [TwoFactorAuthAutoManagedBottomSheet]
     */
    protected fun addTwoFactorAuthOverlay() {
        addComposeOverlay { TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager) }
    }

    /**
     * Enables the auto-managed 2 factor authentication challenge overlay for Compose-based Activities.
     */
    @Composable
    protected fun TwoFactorAuthAutoManagedBottomSheet() {
        TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager)
    }

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
