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
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.core.common.utils.format
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils

class DriveBlockedBottomSheetDialog : InformationBottomSheetDialog() {

    private val navigationArgs: DriveBlockedBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        illu.apply {
            layoutParams.height = 70.toPx()
            layoutParams.width = 70.toPx()
            setImageResource(R.drawable.ic_drive_blocked)
        }

        val drive = DriveInfosController.getDrive(AccountUtils.currentUserId, driveId = navigationArgs.driveId)!!
        title.text = resources.getQuantityString(R.plurals.driveBlockedTitle, 1, drive.name)
        description.text = resources.getQuantityString(R.plurals.driveBlockedDescription, 1, drive.getUpdatedAt().format())
        actionButton.apply {
            setText(R.string.buttonRenew)
            setOnClickListener { requireContext().openUrl(ApiRoutes.renewDrive(drive.accountId)) }
        }
        secondaryActionButton.setText(R.string.buttonClose)
    }

}
