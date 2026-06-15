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
package com.infomaniak.drive.ui.fileList.multiSelect

import android.view.View
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils

class FileListMultiSelectActionsBottomSheetDialog : MultiSelectActionsBottomSheetDialog(MatomoCategory.FileListFileAction) {

    override fun configureCopyToDrive() {
        val isSingleFile = navigationArgs.fileIds.size == 1 && !navigationArgs.isAllSelected
        val userId = navigationArgs.userDrive?.userId ?: AccountUtils.currentUserId
        val hasOtherDrivesAvailable = DriveInfosController.hasEligibleDestinationDrives(userId)
        binding.copyToDrive.apply {
            visibility = if (isSingleFile && hasOtherDrivesAvailable) View.VISIBLE else View.GONE
            setOnClickListener { onActionSelected(SelectDialogAction.COPY_TO_DRIVE) }
        }
    }
}
