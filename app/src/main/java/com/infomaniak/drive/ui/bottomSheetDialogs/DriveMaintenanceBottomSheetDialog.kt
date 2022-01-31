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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class DriveMaintenanceBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        illu.apply {
            layoutParams.height = 70.toPx()
            layoutParams.width = 70.toPx()
            setImageResource(R.drawable.ic_maintenance)
        }

        val driveName = requireArguments().getString(DRIVE_NAME)
        title.text = resources.getQuantityString(R.plurals.driveMaintenanceTitle, 1, driveName)
        description.setText(R.string.driveMaintenanceDescription)
        actionButton.apply {
            setText(R.string.buttonClose)
            setOnClickListener { dismiss() }
        }

        secondaryActionButton.isGone = true
    }

    companion object {
        const val DRIVE_NAME = "driveName"
    }

}