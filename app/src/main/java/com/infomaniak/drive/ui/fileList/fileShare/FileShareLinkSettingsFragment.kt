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
package com.infomaniak.drive.ui.fileList.fileShare

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MatomoUtils.trackEventWithBooleanValue
import com.infomaniak.drive.utils.getBackNavigationResult
import com.infomaniak.drive.utils.showOrHideEmptyError
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.ShareLinkContainerView.Companion.getTypeName
import com.infomaniak.lib.core.utils.*
import kotlinx.android.synthetic.main.fragment_file_share_link_settings.*
import kotlinx.android.synthetic.main.item_dropbox_settings.expirationDateSwitch
import java.util.*

class FileShareLinkSettingsFragment : Fragment() {
    private val navigationArgs: FileShareLinkSettingsFragmentArgs by navArgs()
    private val shareViewModel: FileShareViewModel by viewModels()
    private var defaultCalendarTimestamp: Date = Date().endOfTheDay()

    private lateinit var officePermission: ShareLink.EditPermission
    private lateinit var shareLink: ShareLink

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_share_link_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareLink = navigationArgs.shareLink
        officePermission = when {
            navigationArgs.isFolder -> if (shareLink.canEdit) ShareLink.OfficeFolderPermission.WRITE else ShareLink.OfficeFolderPermission.READ
            else -> if (shareLink.canEdit) ShareLink.OfficeFilePermission.WRITE else ShareLink.OfficeFilePermission.READ
        }

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        shareLink.validUntil?.let { defaultCalendarTimestamp = it }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.OFFICE_EDITING_RIGHTS_NAV_KEY) { bundle ->
            officePermission = bundle.getParcelable(PERMISSION_BUNDLE_KEY)!!
            shareLink.canEdit = officePermission.apiValue
            setupShareLinkSettingsUi()
        }

        setupUiListeners()
        setupFreeAccountUi()
        setupShareLinkSettingsUi()
    }

    private fun setupUiListeners() {
        setupAddPassword()
        setupExpirationDate()
        setupNewPassword()
        setupAllowDownload()
        setupBlockComments()
        setupBlockUsers()
        setupUpgradeOffer()
        setupSaveButton()
        setupFileShareLinkRights()
    }

    private fun setupAddPassword() {
        addPasswordSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (shareLink.permission == ShareLink.ShareLinkFilePermission.PUBLIC) {
                passwordTextLayout.isVisible = isChecked
            } else if (shareLink.permission == ShareLink.ShareLinkFilePermission.PASSWORD) {
                passwordTextLayout.isGone = true
                newPasswordButton.isVisible = isChecked
            }
        }
    }

    private fun setupExpirationDate() {
        addExpirationDateSwitch.setOnCheckedChangeListener { _, isChecked ->
            expirationDateInput.isVisible = isChecked
            expirationTimeInput.isVisible = isChecked
            shareLink.validUntil = if (isChecked) defaultCalendarTimestamp else null
        }
    }

    private fun setupNewPassword() {
        newPasswordButton.setOnClickListener {
            newPasswordButton.isGone = true
            passwordTextLayout.isVisible = true
        }
    }

    private fun setupAllowDownload() {
        allowDownloadValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockDownloads = !isChecked
        }
    }

    private fun setupBlockComments() {
        blockCommentsValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockComments = isChecked
        }
    }

    private fun setupBlockUsers() {
        blockUsersConsultValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.blockInformation = isChecked
        }
    }

    private fun setupUpgradeOffer() {
        val upgradeOfferOnClickListener = View.OnClickListener { safeNavigate(R.id.secureLinkShareBottomSheetDialog) }
        upgradeOfferPassword.setOnClickListener(upgradeOfferOnClickListener)
        upgradeOfferExpirationDate.setOnClickListener(upgradeOfferOnClickListener)
    }

    private fun setupSaveButton() {
        saveButton.apply {
            initProgress(this@FileShareLinkSettingsFragment)
            setOnClickListener {
                showProgress()
                context?.applicationContext?.trackShareSettingsEvent(
                    addPasswordSwitch?.isChecked, expirationDateSwitch?.isChecked, allowDownloadValue?.isChecked
                )
                val isValid = checkPasswordStatus()
                if (!isValid) {
                    hideProgress(R.string.buttonSave)
                } else {
                    val file = File(id = navigationArgs.fileId, driveId = navigationArgs.driveId)
                    shareViewModel.editFileShareLink(file, shareLink).observe(viewLifecycleOwner) { apiResponse ->
                        if (apiResponse.data == true) {
                            findNavController().popBackStack()
                        } else {
                            showSnackbar(R.string.errorModification)
                        }
                        saveButton?.hideProgress(R.string.buttonSave)
                    }
                }
            }
        }
    }

    private fun checkPasswordStatus(): Boolean {
        var isValid = true

        if (addPasswordSwitch.isChecked) {
            val hasError = passwordEditText.showOrHideEmptyError()
            isValid = !(hasError && passwordTextLayout.isVisible)

            val password = passwordEditText.text
            if (password?.isNotBlank() == true) shareLink.apply {
                this.password = password.toString()
                this.permission = ShareLink.ShareLinkFilePermission.PASSWORD
            }

        } else {
            shareLink.permission = ShareLink.ShareLinkFilePermission.PUBLIC
        }

        return isValid
    }

    private fun setupFileShareLinkRights() {
        fileShareLinkRights.setOnClickListener {
            val permissionsGroup =
                if (navigationArgs.isFolder) SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FOLDER_OFFICE
                else SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FILE_OFFICE
            safeNavigate(
                FileShareLinkSettingsFragmentDirections.actionFileShareLinkSettingsFragmentToSelectPermissionBottomSheetDialog(
                    currentPermission = officePermission,
                    currentFileId = navigationArgs.fileId,
                    permissionsGroup = permissionsGroup,
                )
            )
        }
    }

    private fun setupFreeAccountUi() {
        if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {

            addPasswordLayout.apply {
                addPasswordSwitch.isEnabled = false
                addPasswordSwitch.isClickable = false
                upgradeOfferPassword.isVisible = true
            }

            addExpirationDateLayout.apply {
                addExpirationDateSwitch.isEnabled = false
                addExpirationDateSwitch.isClickable = false
                upgradeOfferExpirationDate.isVisible = true
            }
        }
    }

    private fun setupShareLinkSettingsUi() {
        shareLink.apply {

            if (navigationArgs.isOnlyOfficeFile || navigationArgs.isFolder) {
                rightsTitle.isVisible = true
                fileShareLinkRights.isVisible = true
                rightsValue.setText(officePermission.translation)
                rightsIcon.setImageResource(officePermission.icon)
            } else {
                rightsTitle.isGone = true
                fileShareLinkRights.isGone = true
            }

            allowDownloadValue.isChecked = !blockDownloads
            blockCommentsValue.isChecked = blockComments
            blockUsersConsultValue.isChecked = blockInformation

            if (permission == ShareLink.ShareLinkFilePermission.PASSWORD) {
                passwordTextLayout.isGone = true
                newPasswordButton.isVisible = true
                addPasswordSwitch.isChecked = true
            }
            addPasswordDescription.text = getString(
                R.string.shareLinkPasswordRightDescription,
                context?.getString(getTypeName(navigationArgs.isFolder, navigationArgs.isOnlyOfficeFile))
            )

            addExpirationDateSwitch.isChecked = shareLink.validUntil != null
            expirationDateInput.init(
                fragmentManager = parentFragmentManager,
                defaultDate = defaultCalendarTimestamp,
                onDateSet = {
                    val validUntil = Calendar.getInstance().apply {
                        val date = Date(it)
                        set(
                            date.year(),
                            date.month(),
                            date.day(),
                            defaultCalendarTimestamp.hours(),
                            defaultCalendarTimestamp.minutes(),
                        )
                    }.time
                    shareLink.validUntil = validUntil
                    defaultCalendarTimestamp = validUntil
                },
            )
            expirationTimeInput.init(
                fragmentManager = parentFragmentManager,
                defaultDate = defaultCalendarTimestamp,
                onDateSet = { hours, minutes ->
                    val validUntil = Calendar.getInstance().apply {
                        set(
                            defaultCalendarTimestamp.year(),
                            defaultCalendarTimestamp.month(),
                            defaultCalendarTimestamp.day(),
                            hours,
                            minutes,
                        )
                    }.time
                    shareLink.validUntil = validUntil
                    defaultCalendarTimestamp = validUntil
                },
            )
        }
    }

    private fun Context.trackShareSettingsEvent(
        protectWithPassword: Boolean?,
        expirationDate: Boolean?,
        downloadFromLink: Boolean?
    ) {
        val category = "shareAndRights"
        trackEventWithBooleanValue(category, "protectWithPassword", protectWithPassword)
        trackEventWithBooleanValue(category, "expirationDateLink", expirationDate)
        trackEventWithBooleanValue(category, "downloadFromLink", downloadFromLink)
    }
}
