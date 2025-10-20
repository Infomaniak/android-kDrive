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

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.core.twofactorauth.front.addComposeOverlay
import com.infomaniak.core.uiview.edgetoedge.EdgeToEdgeActivity
import com.infomaniak.core.utils.format
import com.infomaniak.drive.KDRIVE_WEB
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.databinding.ActivityNoDriveBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.twoFactorAuthManager
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.launch

class MaintenanceActivity : EdgeToEdgeActivity() {

    private val binding: ActivityNoDriveBinding by lazy { ActivityNoDriveBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)
        addComposeOverlay { TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager) }

        DriveInfosController.getDrives(AccountUtils.currentUserId).apply {
            val firstDrive = firstOrNull()

            val icon = when {
                firstDrive == null -> R.drawable.ic_no_network
                firstDrive.isTechnicalMaintenance -> R.drawable.ic_maintenance
                else -> R.drawable.ic_drive_blocked
            }
            noDriveIconLayout.icon.setImageResource(icon)

            val title = if (firstDrive == null) {
                getString(R.string.errorNetwork)
            } else if (firstDrive.isAsleep) {
                resources.getString(R.string.maintenanceAsleepTitle, firstDrive.name)
            } else {
                resources.getQuantityString(
                    if (firstDrive.isTechnicalMaintenance) R.plurals.driveMaintenanceTitle else R.plurals.driveBlockedTitle,
                    this.size,
                    firstDrive.name
                )
            }
            noDriveTitle.text = title

            noDriveDescription.text = when {
                firstDrive == null -> getString(R.string.connectionError)
                firstDrive.isAsleep -> getString(R.string.maintenanceAsleepDescription)
                firstDrive.isTechnicalMaintenance -> getString(R.string.driveMaintenanceDescription)
                else -> resources.getQuantityString(
                    R.plurals.driveBlockedDescription,
                    this.size,
                    firstDrive.getUpdatedAt().format()
                )
            }

            noDriveActionButton.apply {
                when {
                    firstDrive == null -> isGone = true
                    firstDrive.isAsleep -> {
                        noDriveActionButton.text = getString(R.string.maintenanceWakeUpButton)
                        setOnClickListener { openUrl(KDRIVE_WEB) }
                    }
                    firstDrive.isTechnicalMaintenance -> isGone = true
                    else -> {
                        noDriveActionButton.text = getString(R.string.buttonRenew)
                        setOnClickListener { openUrl(ApiRoutes.renewDrive(firstDrive.accountId)) }
                    }
                }
            }
        }

        anotherProfileButton.setOnClickListener {
            startActivity(Intent(this@MaintenanceActivity, SwitchUserActivity::class.java))
        }

        binding.root.enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AccountUtils.updateCurrentUserAndDrives(this@MaintenanceActivity, fromMaintenance = true)
        }
    }
}
