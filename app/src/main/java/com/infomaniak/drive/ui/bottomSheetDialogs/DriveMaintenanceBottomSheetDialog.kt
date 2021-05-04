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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class DriveMaintenanceBottomSheetDialog : InformationBottomSheetDialog() {
    val navigationArgs: DriveMaintenanceBottomSheetDialogArgs by navArgs()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        illu.apply {
            layoutParams.height = 70.toPx()
            layoutParams.width = 70.toPx()
            setImageResource(R.drawable.ic_maintenance)
        }

        title.text = getString(R.string.driveMaintenanceTitle, navigationArgs.driveName)
        description.setText(R.string.driveMaintenanceDescription)
        secondaryActionButton.visibility = GONE

        actionButton.apply {
            visibility = VISIBLE
            setText(R.string.buttonClose)
            setOnClickListener {
                dismiss()
            }
        }
    }
}