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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
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

    private var showOfficePermission: Boolean = false
    private var officePermission: ShareLink.OfficePermission = ShareLink.OfficePermission.READ

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_share_link_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareLink = navigationArgs.shareLink
        showOfficePermission = navigationArgs.onlyoffice
        officePermission =
            if (shareLink.canEdit) ShareLink.OfficePermission.WRITE
            else ShareLink.OfficePermission.READ

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        shareLink.validUntil?.let { validUntil ->
            if (validUntil.time < Date().time) shareLink.validUntil = null
            else defaultCalendarTimestamp = validUntil
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
            officePermission = bundle.getParcelable(PERMISSION_BUNDLE_KEY)!!
            shareLink.canEdit = officePermission == ShareLink.OfficePermission.WRITE
            setupShareLinkSettingsUi()
        }

        setupUiListeners()

        fileShareLinkRights.setOnClickListener {
            safeNavigate(
                FileShareLinkSettingsFragmentDirections.actionFileShareLinkSettingsFragmentToSelectPermissionBottomSheetDialog(
                    currentPermission = officePermission,
                    currentFileId = navigationArgs.fileId,
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_OFFICE
                )
            )
        }

        if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {

            addPasswordLayout.apply {
                addPasswordSwitch.isEnabled = false
                addPasswordSwitch.isClickable = false
                upgradeOfferPassword.visibility = VISIBLE
            }

            settingsLayout?.apply {
                val beforeLastIndex = indexOfChild(saveButton) - 1
                removeView(addExpirationDateLayout)
                addView(addExpirationDateLayout, beforeLastIndex)
            }

            addExpirationDateLayout.apply {
                addExpirationDateSwitch.isEnabled = false
                addExpirationDateSwitch.isClickable = false
                upgradeOfferExpirationDate.visibility = VISIBLE
            }
        }

        setupShareLinkSettingsUi()
    }

    private fun setupShareLinkSettingsUi() {
        shareLink.apply {

            if (showOfficePermission) {
                fileShareLinkRights.visibility = VISIBLE
                rightsValue.setText(officePermission.translation)
                rightsIcon.load(officePermission.icon)
            } else {
                fileShareLinkRights.visibility = GONE
            }

            allowDownloadValue.isChecked = !blockDownloads
            blockCommentsValue.isChecked = blockComments
            blockUsersConsultValue.isChecked = blockInformation

            if (permission == ShareLink.ShareLinkPermission.PASSWORD) {
                passwordTextLayout.visibility = GONE
                newPasswordButton.visibility = VISIBLE
                addPasswordSwitch.isChecked = true
            }

            addExpirationDateSwitch.isChecked = shareLink.validUntil != null
            expirationDateInput.init(fragmentManager = parentFragmentManager, defaultCalendarTimestamp) {
                val validUntil = Date(it)
                shareLink.validUntil = validUntil
                defaultCalendarTimestamp = validUntil
            }
        }
    }

    private fun setupUiListeners() {

        addPasswordSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (shareLink.permission == ShareLink.ShareLinkPermission.PUBLIC) {
                passwordTextLayout.isVisible = isChecked
            } else if (shareLink.permission == ShareLink.ShareLinkPermission.PASSWORD) {
                passwordTextLayout.visibility = GONE
                newPasswordButton.isVisible = isChecked
            }
        }

        addExpirationDateSwitch.setOnCheckedChangeListener { _, isChecked ->
            expirationDateInput.isVisible = isChecked
            shareLink.validUntil = if (isChecked) defaultCalendarTimestamp else null
        }

        newPasswordButton.setOnClickListener {
            newPasswordButton.visibility = GONE
            passwordTextLayout.visibility = VISIBLE
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

        val upgradeOfferOnClickListener = View.OnClickListener { safeNavigate(R.id.secureLinkShareBottomSheetDialog) }
        upgradeOfferPassword.setOnClickListener(upgradeOfferOnClickListener)
        upgradeOfferExpirationDate.setOnClickListener(upgradeOfferOnClickListener)

        saveButton.initProgress(this)
        saveButton.setOnClickListener {
            saveButton.showProgress()
            val file = File(id = navigationArgs.fileId, driveId = navigationArgs.driveId)

            var isValid = true
            if (addPasswordSwitch.isChecked) {

                val hasError = passwordEditText.showOrHideEmptyError()
                isValid = !(hasError && passwordTextLayout.isVisible)

                val password = passwordEditText.text
                if (password?.isNotBlank() == true) shareLink.apply {
                    this.password = password.toString()
                    this.permission = ShareLink.ShareLinkPermission.PASSWORD
                }

            } else {
                shareLink.permission = ShareLink.ShareLinkPermission.PUBLIC
            }

            if (!isValid) {
                saveButton?.hideProgress(R.string.buttonSave)
            } else {

                shareLink.validUntil = shareLink.validUntil?.endOfTheDay()

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
}