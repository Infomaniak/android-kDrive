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
package com.infomaniak.drive.ui.fileList.fileShare

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
import com.infomaniak.core.myksuite.ui.screens.KSuiteApp
import com.infomaniak.core.myksuite.ui.utils.MyKSuiteUiUtils.openMyKSuiteUpgradeBottomSheet
import com.infomaniak.core.utils.*
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.databinding.FragmentFileShareLinkSettingsBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.showOrHideEmptyError
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.drive.views.ShareLinkContainerView.Companion.getTypeName
import com.infomaniak.lib.core.utils.*
import java.util.Calendar
import java.util.Date

class FileShareLinkSettingsFragment : Fragment() {

    private var binding: FragmentFileShareLinkSettingsBinding by safeBinding()

    private val navigationArgs: FileShareLinkSettingsFragmentArgs by navArgs()
    private val shareViewModel: FileShareViewModel by viewModels()
    private val shareLink: ShareLink by lazy { navigationArgs.shareLink }

    private var defaultCalendarTimestamp: Date = Date().endOfTheDay()
    private lateinit var officePermission: ShareLink.EditPermission

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFileShareLinkSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initOfficePermission()

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        shareLink.validUntil?.let { defaultCalendarTimestamp = it }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.OFFICE_EDITING_RIGHTS_NAV_KEY) { bundle ->
            officePermission = bundle.getParcelable(PERMISSION_BUNDLE_KEY)!!
            shareLink.capabilities?.canEdit = officePermission.apiValue
            setupShareLinkSettingsUi()
        }

        setupUiListeners()
        setupFreeAccountUi()
        setupShareLinkSettingsUi()
    }

    private fun initOfficePermission() {
        val canEdit = shareLink.capabilities?.canEdit == true
        officePermission = when {
            navigationArgs.isFolder -> if (canEdit) ShareLink.OfficeFolderPermission.WRITE else ShareLink.OfficeFolderPermission.READ
            else -> if (canEdit) ShareLink.OfficeFilePermission.WRITE else ShareLink.OfficeFilePermission.READ
        }
    }

    private fun setupUiListeners() {
        setupAddPassword()
        setupExpirationDate()
        setupNewPassword()
        setupAllowDownload()
        setupSaveButton()
        setupFileShareLinkRights()
    }

    private fun setupAddPassword() = with(binding) {
        addPasswordSwitch.setOnCheckedChangeListener { _, isChecked ->
            val actionPasswordVisible = shareLink.right == ShareLink.ShareLinkFilePermission.PASSWORD
            newPasswordButton.isVisible = actionPasswordVisible && isChecked
            passwordTextLayout.isVisible = !actionPasswordVisible && isChecked
        }
    }

    private fun setupExpirationDate() = with(binding) {
        addExpirationDateSwitch.setOnCheckedChangeListener { _, isChecked ->
            expirationDateInput.isVisible = isChecked
            expirationTimeInput.isVisible = isChecked
            shareLink.validUntil = if (isChecked) defaultCalendarTimestamp else null
        }
    }

    private fun setupNewPassword() = with(binding) {
        newPasswordButton.setOnClickListener {
            newPasswordButton.isGone = true
            passwordTextLayout.isVisible = true
        }
    }

    private fun setupAllowDownload() {
        binding.allowDownloadValue.setOnCheckedChangeListener { _, isChecked ->
            shareLink.capabilities?.canDownload = isChecked
        }
    }

    private fun setupUpgradeOfferListener() {
        val upgradeOfferOnClickListener = View.OnClickListener {
            findNavController().openMyKSuiteUpgradeBottomSheet(KSuiteApp.Drive)
        }
        binding.addPasswordLayout.setOnClickListener(upgradeOfferOnClickListener)
        binding.addExpirationDateLayout.setOnClickListener(upgradeOfferOnClickListener)
    }

    private fun setupSaveButton() = with(binding) {
        saveButton.apply {
            initProgress(this@FileShareLinkSettingsFragment)
            setOnClickListener {
                showProgressCatching()
                trackShareRightsEvents(
                    protectWithPassword = addPasswordSwitch.isChecked,
                    expirationDate = addExpirationDateSwitch.isChecked,
                    downloadFromLink = allowDownloadValue.isChecked,
                )
                val isValid = checkPasswordStatus()
                if (!isValid) {
                    hideProgressCatching(R.string.buttonSave)
                } else {
                    val file = File(id = navigationArgs.fileId, driveId = navigationArgs.driveId)
                    shareViewModel.editFileShareLink(file, shareLink).observe(viewLifecycleOwner) { apiResponse ->
                        if (apiResponse.data == true) {
                            findNavController().popBackStack()
                        } else {
                            showSnackbar(R.string.errorModification)
                        }
                        saveButton.hideProgressCatching(R.string.buttonSave)
                    }
                }
            }
        }
    }

    private fun checkPasswordStatus(): Boolean = with(binding) {
        var isValid = true

        if (addPasswordSwitch.isChecked) {
            val hasError = passwordEditText.showOrHideEmptyError()
            isValid = !(hasError && passwordTextLayout.isVisible)

            val password = passwordEditText.text
            if (password?.isNotBlank() == true) shareLink.apply {
                this.newPassword = password.toString()
                this.newRight = ShareLink.ShareLinkFilePermission.PASSWORD
            }

        } else {
            shareLink.newRight = ShareLink.ShareLinkFilePermission.PUBLIC
        }

        return isValid
    }

    private fun setupFileShareLinkRights() {
        binding.fileShareLinkRights.setOnClickListener {
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

    private fun setupFreeAccountUi() = with(binding) {
        if (AccountUtils.getCurrentDrive()?.isFreeTier == true) {
            setupUpgradeOfferListener()

            addPasswordSwitch.isEnabled = false
            addPasswordSwitch.isClickable = false
            upgradeOfferPassword.isVisible = true
            offerPasswordMyKSuitePlusChip.isVisible = true

            addExpirationDateSwitch.isEnabled = false
            addExpirationDateSwitch.isClickable = false
            upgradeOfferExpirationDate.isVisible = true
            offerExpirationMyKSuitePlusChip.isVisible = true
        }
    }

    private fun setupShareLinkSettingsUi() = with(binding) {
        if (navigationArgs.isOnlyOfficeFile || navigationArgs.isFolder) {
            rightsTitle.isVisible = true
            fileShareLinkRights.isVisible = true
            rightsValue.setText(officePermission.translation)
            rightsIcon.setImageResource(officePermission.icon)
        } else {
            rightsTitle.isGone = true
            fileShareLinkRights.isGone = true
        }

        allowDownloadValue.isChecked = shareLink.capabilities?.canDownload == true

        if (shareLink.right == ShareLink.ShareLinkFilePermission.PASSWORD) {
            passwordTextLayout.isGone = true
            newPasswordButton.isVisible = true
            addPasswordSwitch.isChecked = true
        }
        addPasswordDescription.text = getString(
            R.string.shareLinkPasswordRightDescription,
            context.getString(getTypeName(navigationArgs.isFolder, navigationArgs.isOnlyOfficeFile))
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

    private fun trackShareRightsEvents(protectWithPassword: Boolean?, expirationDate: Boolean?, downloadFromLink: Boolean?) {
        trackShareRightsEvent("protectWithPassword", value = protectWithPassword?.toFloat())
        trackShareRightsEvent("expirationDateLink", value = expirationDate?.toFloat())
        trackShareRightsEvent("downloadFromLink", value = downloadFromLink?.toFloat())
    }
}
