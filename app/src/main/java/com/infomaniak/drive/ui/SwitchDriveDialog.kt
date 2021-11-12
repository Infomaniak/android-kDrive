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

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.ui.bottomSheetDialogs.DriveMaintenanceBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.views.SelectBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select.*

class SwitchDriveDialog : SelectBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectTitle.setText(R.string.buttonSwitchDrive)

        val driveList = DriveInfosController.getDrives(AccountUtils.currentUserId)
        selectRecyclerView.adapter = SwitchDriveBottomSheetAdapter(driveList) { drive ->
            findNavController().popBackStack()
            // TODO - Implement drive blocked BottomSheetDialog (for invoice issues) - Awaiting API attributes
            if (drive.maintenance) {
                findNavController().navigate(
                    R.id.driveMaintenanceBottomSheetFragment,
                    bundleOf(DriveMaintenanceBottomSheetDialog.DRIVE_NAME to drive.name)
                )
            } else {
                AccountUtils.currentDriveId = drive.id
                (activity as? MainActivity)?.saveLastNavigationItemSelected()
                AccountUtils.reloadApp?.invoke()
            }
        }
    }
}
