/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList

import androidx.fragment.app.Fragment
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.ShareLinkContainerView

interface ShareLinkManageable {

    val shareLinkViewModel: ShareLinkViewModel
    val shareLinkContainerView: ShareLinkContainerView?

    fun Fragment.createShareLink(file: File) {
        shareLinkViewModel.createShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                shareLinkContainerView?.update(apiResponse.data)
            } else {
                showSnackbar(apiResponse.translateError())
            }
        }
    }

    fun Fragment.deleteShareLink(file: File) {
        shareLinkViewModel.deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.data == true) {
                shareLinkContainerView?.update(null)
            } else {
                showSnackbar(apiResponse.translateError())
            }
        }
    }
}
