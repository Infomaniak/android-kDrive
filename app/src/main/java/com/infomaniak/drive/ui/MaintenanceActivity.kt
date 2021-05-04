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
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.android.synthetic.main.activity_no_drive.*
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MaintenanceActivity : AppCompatActivity() {

    private val isTechnicalMaintenance: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_drive)

        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            if (isTechnicalMaintenance) {
                noDriveTitle.text = getString(R.string.driveMaintenanceTitle, currentDrive.name)
                noDriveDescription.text = getString(R.string.driveMaintenanceDescription)
                noDriveIconLayout.icon.setImageResource(R.drawable.ic_maintenance)
                noDriveActionButton.visibility = GONE
            } else {
                noDriveTitle.text = getString(R.string.driveBlockedTitle, currentDrive.name)
                noDriveDescription.text = getString(R.string.driveBlockedDescription)
                noDriveIconLayout.icon.setImageResource(R.drawable.ic_drive_blocked)
                noDriveActionButton.visibility = VISIBLE
                noDriveActionButton.text = getString(R.string.buttonRenew)
            }

            anotherProfileButton.setOnClickListener {
                GlobalScope.launch {
                    if (AccountUtils.getAllUsersSync().size > 1) {
                        startActivity(Intent(this@MaintenanceActivity, MainActivity::class.java))
                        // TODO : Display user list to allow switch
                    } else {
                        startActivity(Intent(this@MaintenanceActivity, LoginActivity::class.java))
                    }
                }
            }
        }
    }
}