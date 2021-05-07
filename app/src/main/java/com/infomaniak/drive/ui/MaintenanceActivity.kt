/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.view.View.GONE
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.activity_no_drive.*
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.coroutines.launch

class MaintenanceActivity : AppCompatActivity() {

    private val isTechnicalMaintenance: Boolean = true // TODO - Default value to true until API available for invoice issues

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_drive)

        noDriveIconLayout.icon.setImageResource(if (isTechnicalMaintenance) R.drawable.ic_maintenance else R.drawable.ic_drive_blocked)

        DriveInfosController.getDrives(AccountUtils.currentUserId).apply {
            noDriveTitle.text = resources.getQuantityString(
                if (isTechnicalMaintenance) R.plurals.driveMaintenanceTitle else R.plurals.driveBlockedTitle,
                this.size,
                this.firstOrNull()?.name
            )
            noDriveDescription.text = if (isTechnicalMaintenance) {
                resources.getQuantityString(
                    R.plurals.driveBlockedDescription,
                    this.size,
                    this.firstOrNull()?.name
                )
            } else getString(R.string.driveMaintenanceDescription)
        }

        noDriveActionButton.apply {
            if (isTechnicalMaintenance) {
                visibility = GONE
            } else {
                noDriveActionButton.text = getString(R.string.buttonRenew)
                setOnClickListener {
                    openUrl(ApiRoutes.orderDrive())
                }
            }
        }

        anotherProfileButton.setOnClickListener {
            startActivity(Intent(this@MaintenanceActivity, SwitchUserActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AccountUtils.updateCurrentUserAndDrives(this@MaintenanceActivity, fromMaintenance = true)
        }
    }
}