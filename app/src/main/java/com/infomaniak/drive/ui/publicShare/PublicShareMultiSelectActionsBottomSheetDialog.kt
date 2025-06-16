/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.publicShare

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.infomaniak.drive.MatomoDrive.PUBLIC_SHARE_ACTION_CATEGORY
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar

class PublicShareMultiSelectActionsBottomSheetDialog : MultiSelectActionsBottomSheetDialog(MATOMO_CATEGORY) {

    private val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onlyDisplayDownloadAction()
        observeArchiveUuid()
    }

    override fun downloadArchive() {
        publicShareViewModel.buildArchive(getArchiveBody())
    }

    private fun onlyDisplayDownloadAction() = with(binding) {
        downloadFile.isVisible = true

        manageCategories.isGone = true
        addFavorites.isGone = true
        coloredFolder.isGone = true
        availableOffline.isGone = true
        moveFile.isGone = true
        duplicateFile.isGone = true
        restoreFileIn.isGone = true
        restoreFileToOriginalPlace.isGone = true
        deletePermanently.isGone = true
    }

    private fun observeArchiveUuid() = with(publicShareViewModel) {
        buildArchiveResult.observe(viewLifecycleOwner) { (error, archiveUuid) ->
            archiveUuid?.let {
                val downloadURL = ApiRoutes.downloadPublicShareArchive(driveId, publicShareUuid, it.uuid)
                val userBearerToken = AccountUtils.currentUser?.apiToken?.accessToken
                DownloadManagerUtils.scheduleDownload(requireContext(), downloadURL, ARCHIVE_FILE_NAME, userBearerToken)
            }
            error?.let { showSnackbar(it, anchor = (requireActivity() as PublicShareActivity).getMainButton()) }
            onActionSelected()
        }
    }

    companion object {
        const val MATOMO_CATEGORY = PUBLIC_SHARE_ACTION_CATEGORY
    }
}
