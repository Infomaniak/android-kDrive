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
package com.infomaniak.drive.ui.fileList.multiSelect

import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.data.cache.FileController
import kotlinx.android.synthetic.main.fragment_bottom_sheet_multi_select_actions.*

class FileListMultiSelectActionsBottomSheetDialog : MultiSelectActionsBottomSheetDialog() {

    override fun configureColoredFolder(areIndividualActionsVisible: Boolean) = with(navigationArgs) {
        if (areIndividualActionsVisible && !isFromGallery) {
            disabledColoredFolder.isGone = computeColoredFolderAvailability(fileIds)
            coloredFolder.apply {
                setOnClickListener { onActionSelected(SelectDialogAction.COLOR_FOLDER) }
                isVisible = true
            }
        }
    }

    private fun computeColoredFolderAvailability(fileIds: IntArray): Boolean {
        fileIds.forEach {
            val file = FileController.getFileById(it)
            if (file?.isAllowedToBeColored() == true) return true
        }
        return false
    }
}
