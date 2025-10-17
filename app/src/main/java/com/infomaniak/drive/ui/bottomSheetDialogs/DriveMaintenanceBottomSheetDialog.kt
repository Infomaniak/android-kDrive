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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.drive.KDRIVE_WEB
import com.infomaniak.drive.R

class DriveMaintenanceBottomSheetDialog : InformationBottomSheetDialog() {

    private val navigationArgs: DriveMaintenanceBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        illu.apply {
            layoutParams.height = 70.toPx()
            layoutParams.width = 70.toPx()
            setImageResource(R.drawable.ic_maintenance)
        }

        if (navigationArgs.isAsleep) {
            title.text = resources.getString(R.string.maintenanceAsleepTitle, navigationArgs.driveName)
            description.text = getString(R.string.maintenanceAsleepDescription)
            actionButton.apply {
                setText(R.string.buttonClose)
                setOnClickListener { dismiss() }
            }
            secondaryActionButton.apply {
                setText(R.string.maintenanceWakeUpButton)
                setOnClickListener { context.openUrl(KDRIVE_WEB) }
            }
        } else {
            title.text = resources.getQuantityString(R.plurals.driveMaintenanceTitle, 1, navigationArgs.driveName)
            description.text = getString(R.string.driveMaintenanceDescription)
            actionButton.apply {
                setText(R.string.buttonClose)
                setOnClickListener { dismiss() }
            }

            secondaryActionButton.isGone = true
        }
    }

}
