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
import android.view.View
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class ColorFolderUpgradeBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title.setText(R.string.folderColorTitle)
        description.setText(R.string.folderColorDescription)
        illu.setAnimation(R.raw.illu_upgrade)

        actionButton.setText(R.string.buttonUpgradeOffer)
        actionButton.setOnClickListener {
            requireContext().openUrl(ApiRoutes.upgradeDrive(AccountUtils.currentDriveId))
            dismiss()
        }

        packAvailability.isVisible = true
        packAvailability.text = getString(R.string.allPackAvailability, "Solo, Team & Pro")
    }
}
