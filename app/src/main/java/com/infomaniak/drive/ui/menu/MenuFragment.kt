/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat.setTint
import androidx.core.graphics.drawable.DrawableCompat.wrap
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.coil.loadAvatar
import com.infomaniak.core.common.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.databinding.FragmentMenuBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MenuViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.openKSuiteUpgradeBottomSheet
import com.infomaniak.drive.utils.openSupport

class MenuFragment : Fragment() {

    private var binding: FragmentMenuBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    private val menuViewModel: MenuViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        menuUploadFileInProgressView.setFolderId(folderId = OTHER_ROOT_ID)

        val user = AccountUtils.currentUser ?: return@with

        userName.text = user.displayName
        userEmail.text = user.email
        userImage.loadAvatar(id = user.id, avatarUrl = user.avatar, initials = user.getInitials())

        if (DriveInfosController.hasSingleDrive(user.id)) {
            driveIcon.isGone = true
        } else {
            driveSwitchContainer.setOnClickListener { safelyNavigate(R.id.switchDriveDialog) }
        }

        changeUser.setOnClickListener {
            val switchUserExtra = FragmentNavigatorExtras(changeUser to changeUser.transitionName)
            safelyNavigate(R.id.switchUserActivity, null, null, switchUserExtra)
        }

        settings.setOnClickListener {
            safelyNavigate(MenuFragmentDirections.actionMenuFragmentToSettingsFragment())
        }

        support.setOnClickListener { requireContext().openSupport() }

        logout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogStyle)
                .setTitle(getString(R.string.alertRemoveUserTitle))
                .setMessage(getString(R.string.alertRemoveUserDescription, user.displayName))
                .setPositiveButton(R.string.buttonConfirm) { _, _ -> menuViewModel.logout(user) }
                .setNegativeButton(R.string.buttonCancel) { _, _ -> }
                .setCancelable(false).show()
        }

        val drive = AccountUtils.getCurrentDrive() ?: return@with

        driveName.text = drive.name
        driveName.setCompoundDrawables(
            wrap(driveName.compoundDrawablesRelative.first()).apply {
                setTint(this, drive.preferences.color.toColorInt())
            },
            null, null, null,
        )

        val isDriveEmpty = drive.size == 0L
        driveStorageProgress.isInvisible = isDriveEmpty
        if (!isDriveEmpty) {
            val progress = (drive.usedSize.toDouble() / drive.size.toDouble()) * 1_000.toDouble()
            progressDriveQuota.progress = progress.toInt()
            progressDriveQuota.max = 1_000

            val usedSize = requireContext().formatShortFileSize(drive.usedSize)
            val totalSize = requireContext().formatShortFileSize(drive.size)
            textDriveQuota.text = "$usedSize / $totalSize"
        }

        val isKSuiteProFree = drive.kSuite is KSuite.Pro.Free
        kSuiteProCard.isVisible = isKSuiteProFree
        if (isKSuiteProFree) {
            kSuiteProCard.setOnClick { openKSuiteUpgradeBottomSheet(MatomoName.OpenFromUserMenuCard.value, drive) }
        }
    }
}
