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
import android.view.View.VISIBLE
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class DriveBlockedBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title.text = getString(R.string.maintenanceBlockedDriveTitle, AccountUtils.getCurrentDrive()?.name)
        description.setText(R.string.maintenanceBlockedDriveDescription)
        illu.apply {
            layoutParams.height = 70.toPx()
            layoutParams.width = 70.toPx()
            setImageResource(R.drawable.ic_drive_blocked)
        }
        actionButton.apply {
            visibility = VISIBLE
            setText(R.string.buttonRenew)
            setOnClickListener {
                requireContext().openUrl("TODO") // Open renew URL from product, TODO : Awaiting invoices scope
            }
        }
        secondaryActionButton.apply {
            visibility = VISIBLE
            setText(R.string.buttonClose)
            setOnClickListener {
                dismiss()
            }
        }
    }
}