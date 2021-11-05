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
package com.infomaniak.drive.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.CURRENT_FILE_ID_ARG
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.CURRENT_PERMISSION_ARG
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSIONS_GROUP_ARG
import com.infomaniak.drive.utils.loadGlide
import kotlinx.android.synthetic.main.view_share_link_container.view.*

class ShareLinkContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var shareLink: ShareLink? = null
    private var urlValue: String = ""
    private var showOfficePermission: Boolean = false
    private lateinit var currentFile: File
    var officePermission: ShareLink.OfficePermission = ShareLink.OfficePermission.READ
        set(value) {
            field = value
            updateUi()
        }

    init {
        inflate(context, R.layout.view_share_link_container, this)
    }

    private fun onSettingsClicked() {
        findNavController().navigate(
            R.id.fileShareLinkSettingsFragment, bundleOf(
                "fileId" to currentFile.id,
                "driveId" to currentFile.driveId,
                "shareLink" to shareLink!! // cannot be null, if null, settings will not appear
            )
        )
    }

    private fun onOfficePermissionClicked() {
        findNavController().navigate(
            R.id.selectPermissionBottomSheetDialog, bundleOf(
                CURRENT_FILE_ID_ARG to currentFile.id,
                CURRENT_PERMISSION_ARG to officePermission,
                PERMISSIONS_GROUP_ARG to SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_OFFICE
            )
        )
    }

    fun setup(
        shareLink: ShareLink?,
        title: Int = R.string.sharedLinkTitle,
        file: File,
        onSwitchClicked: (isChecked: Boolean) -> Unit,
    ) {
        this.currentFile = file
        this.shareLink = shareLink
        this.visibility = VISIBLE
        this.urlValue = file.shareLink ?: ""
        this.showOfficePermission = file.onlyoffice
        this.officePermission =
            if (shareLink?.canEdit == true) ShareLink.OfficePermission.WRITE
            else ShareLink.OfficePermission.READ

        updateUi()
        shareLinkTitle.setText(title)
        shareLinkSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (shareLinkSwitch.isPressed) {
                toggleSwitchingApproval(false)
                onSwitchClicked(isChecked)
            }
        }
        shareLinkSettings.setOnClickListener {
            onSettingsClicked()
        }
        officePermissionView.setOnClickListener {
            onOfficePermissionClicked()
        }
    }

    fun update(shareLink: ShareLink? = null) {
        this.shareLink = shareLink
        if (shareLink == null) urlValue = ""
        updateUi()
    }

    fun toggleSwitchingApproval(allow: Boolean, isChecked: Boolean? = null) {
        shareLinkSwitch.isClickable = allow
        isChecked?.let { shareLinkSwitch.isChecked = it }
    }

    private fun updateUi() {
        if (shareLink == null && urlValue.isBlank()) {
            shareLinkStatus.setText(R.string.allDisabled)
            shareLinkSwitch.isChecked = false
            shareLinkUrl.visibility = View.GONE
            officePermissionView.visibility = View.GONE
            shareLinkSettings.visibility = View.GONE
        } else {
            shareLinkStatus.setText(R.string.allActivated)
            shareLinkSwitch.isChecked = true
            shareLinkUrl.setUrl(shareLink?.url ?: urlValue)
            shareLinkUrl.visibility = View.VISIBLE
            shareLinkSettings.visibility = View.VISIBLE
            shareLinkSettings.isEnabled = shareLink != null

            if (showOfficePermission) {
                officePermissionView.visibility = View.VISIBLE
                officePermissionValue.setText(officePermission.translation)
                officePermissionIcon.loadGlide(officePermission.icon)
            } else {
                officePermissionView.visibility = View.GONE
            }
        }
    }
}
