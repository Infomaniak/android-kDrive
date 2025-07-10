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
package com.infomaniak.drive.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.MatomoDrive
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.ui.bottomSheetDialogs.DriveBlockedBottomSheetDialogArgs
import com.infomaniak.drive.ui.bottomSheetDialogs.DriveMaintenanceBottomSheetDialogArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.views.SelectBottomSheetDialog

class SwitchDriveDialog : SelectBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        selectTitle.setText(R.string.buttonSwitchDrive)

        val driveList = DriveInfosController.getDrives(AccountUtils.currentUserId)
        selectRecyclerView.adapter = SwitchDriveBottomSheetAdapter(driveList) { drive ->
            trackEvent(MatomoCategory.Drive.categoryName, MatomoName.Switch.eventName)
            findNavController().popBackStack()
            if (drive.maintenance) {
                if (drive.isTechnicalMaintenance) {
                    findNavController().navigate(
                        R.id.driveMaintenanceBottomSheetFragment,
                        DriveMaintenanceBottomSheetDialogArgs(drive.name).toBundle()
                    )
                } else {
                    findNavController().navigate(
                        R.id.driveBlockedBottomSheetFragment,
                        DriveBlockedBottomSheetDialogArgs(drive.id).toBundle()
                    )
                }
            } else {
                AccountUtils.currentDriveId = drive.id
                (activity as? MainActivity)?.saveLastNavigationItemSelected()
                AccountUtils.reloadApp?.invoke(bundleOf())
            }
        }
    }
}
