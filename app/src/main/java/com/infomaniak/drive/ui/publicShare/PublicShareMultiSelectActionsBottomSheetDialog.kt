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
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.network.networking.HttpUtils
import com.infomaniak.core.network.networking.ManualAuthorizationRequired
import com.infomaniak.core.utils.DownloadManagerUtils
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils

class PublicShareMultiSelectActionsBottomSheetDialog : MultiSelectActionsBottomSheetDialog(MatomoCategory.PublicShareAction) {

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

    @OptIn(ManualAuthorizationRequired::class)
    private fun observeArchiveUuid() = with(publicShareViewModel) {
        buildArchiveResult.observe(viewLifecycleOwner) { (error, archiveUuid) ->
            archiveUuid?.let {
                val downloadURL = ApiRoutes.downloadPublicShareArchive(driveId, publicShareUuid, it.uuid)
                val userBearerToken = AccountUtils.currentUser?.apiToken?.accessToken
                DownloadManagerUtils.scheduleDownload(
                    context = requireContext(),
                    url = downloadURL,
                    name = ARCHIVE_FILE_NAME,
                    userBearerToken = userBearerToken,
                    extraHeaders = HttpUtils.getHeaders(),
                    onError = { messageResId -> showSnackbar(title = messageResId) }
                )
            }
            error?.let { showSnackbar(it, anchor = (requireActivity() as PublicShareActivity).getMainButton()) }
            onActionSelected()
        }
    }
}
