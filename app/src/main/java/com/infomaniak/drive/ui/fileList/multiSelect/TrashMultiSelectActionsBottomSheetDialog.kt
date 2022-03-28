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
import com.infomaniak.drive.ui.menu.TrashFragment
import kotlinx.android.synthetic.main.fragment_bottom_sheet_multi_select_actions.*

class TrashMultiSelectActionsBottomSheetDialog : MultiSelectActionsBottomSheetDialog(TrashFragment.MATOMO_CATEGORY) {

    override fun configureColoredFolder(areIndividualActionsVisible: Boolean) {
        coloredFolder.isGone = true
    }

    override fun configureAddFavorites(areIndividualActionsVisible: Boolean) {
        addFavorites.isGone = true
    }

    override fun configureAvailableOffline() {
        availableOffline.isGone = true
    }

    override fun configureDownload() {
        downloadFile.isGone = true
    }

    override fun configureDuplicateFile() {
        duplicateFile.isGone = true
    }

    override fun configureRestoreFileIn() {
        restoreFileIn.apply {
            isVisible = true
            setOnClickListener { onActionSelected(SelectDialogAction.RESTORE_IN) }
        }
    }

    override fun configureRestoreFileToOriginalPlace() {
        restoreFileToOriginalPlace.apply {
            isVisible = true
            setOnClickListener { onActionSelected(SelectDialogAction.RESTORE_TO_ORIGIN) }
        }
    }

    override fun configureDeletePermanently() {
        deletePermanently.apply {
            isVisible = true
            setOnClickListener { onActionSelected(SelectDialogAction.DELETE_PERMANENTLY) }
        }
    }
}
