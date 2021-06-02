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
package com.infomaniak.drive.ui.fileList.fileShare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_file_share_link_settings.*
import java.util.*

class FileShareLinkSettingsFragment : Fragment() {
    private lateinit var shareLink: ShareLink
    private val navigationArgs: FileShareLinkSettingsFragmentArgs by navArgs()
    private var defaultCalendarTimestamp: Date = Date()
    private val shareViewModel: FileShareViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_share_link_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareLink = navigationArgs.shareLink

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        shareLink.validUntil?.let { validUntil ->
            if (validUntil.time < Date().time) shareLink.validUntil = null
            else defaultCalendarTimestamp = validUntil
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
            shareLink.permission = bundle.getParcelable(PERMISSION_BUNDLE_KEY)!!
            setupShareLinkSettingsUi(firstLoad = false)
        }

        setupUiListeners()

        fileShareLinkRights.setOnClickListener {
            safeNavigate(
                FileShareLinkSettingsFragmentDirections.actionFileShareLinkSettingsFragmentToSelectPermissionBottomSheetDialog(
                    currentPermission = shareLink.permission,
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_SETTINGS
                )
            )
        }

        if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {
            val beforeLastIndex = settingsLayout.indexOfChild(saveButton) - 1
            val expirationDateLayout = addExpirationDateLayout
            settingsLayout.removeView(expirationDateLayout)
            settingsLayout.addView(expirationDateLayout, beforeLastIndex)

            expirationDateLayout.apply {
                addExpirationDateSwitch.isEnabled = false
                addExpirationDateSwitch.isClickable = false
                upgradeOffer.visibility = VISIBLE
            }
        }

        setupShareLinkSettingsUi()
    }

    private fun setupShareLinkSettingsUi(firstLoad: Boolean = true) {
        shareLink.apply {
            rightsValue.setText(permission.translation)
            rightsIcon.setImageResource(permission.icon)
            allowDownloadValue.isChecked = !blockDownloads
            blockCommentsValue.isChecked = blockComments
            blockUsersConsultValue.isChecked = blockInformation

            if (permission == ShareLink.ShareLinkPermission.PASSWORD) {
                if (firstLoad) {
                    passwordTextLayout.visibility = GONE
                    newPasswordButton.visibility = VISIBLE
                } else {
                    showPasswordLayout()
                }
            } else {
                passwordTextLayout.visibility = GONE
                newPasswordButton.visibility = GONE
            }

            addExpirationDateSwitch.isChecked = shareLink.validUntil != null
            expirationDateInput.init(fragmentManager = parentFragmentManager, defaultCalendarTimestamp) {
                shareLink.validUntil = Date(it)
            }
        }
    }

    private fun setupUiListeners() {
        addExpirationDateSwitch.setOnCheckedChangeListener { _, isChecked ->
            expirationDateInput.visibility = if (isChecked) VISIBLE else GONE
            shareLink.validUntil = if (isChecked) defaultCalendarTimestamp else null
        }

        newPasswordButton.setOnClickListener {
            showPasswordLayout()
        }

        allowDownloadValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockDownloads = !isChecked
        }

        blockCommentsValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockComments = isChecked
        }

        blockUsersConsultValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockInformation = isChecked
        }

        upgradeOffer.setOnClickListener {
            safeNavigate(R.id.secureLinkShareBottomSheetDialog)
        }

        saveButton.initProgress(this)
        saveButton.setOnClickListener {
            saveButton.showProgress()
            val file = File(id = navigationArgs.fileId, driveId = navigationArgs.driveId)

            var isValid = true
            if (shareLink.permission == ShareLink.ShareLinkPermission.PASSWORD) {
                isValid = !passwordEditText.showOrHideEmptyError()
                shareLink.password = passwordEditText.text.toString()
            }
            if (isValid) {
                shareViewModel.editFileShareLink(file, shareLink).observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.data == true) {
                        findNavController().popBackStack()
                    } else {
                        requireActivity().showSnackbar(R.string.errorModification)
                    }
                    saveButton?.hideProgress(R.string.buttonSave)
                }
            }
        }
    }

    private fun showPasswordLayout() {
        newPasswordButton.visibility = GONE
        passwordTextLayout.visibility = VISIBLE
    }
}