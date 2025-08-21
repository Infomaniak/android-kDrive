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
package com.infomaniak.drive.ui.menu

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat.setTint
import androidx.core.graphics.drawable.DrawableCompat.wrap
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.FragmentMenuBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.openKSuiteProBottomSheet
import com.infomaniak.drive.utils.openSupport
import com.infomaniak.drive.utils.setupRootPendingFilesIndicator
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MenuFragment : Fragment() {

    private var binding: FragmentMenuBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        AccountUtils.currentUser?.let { currentUser ->
            userName.text = currentUser.displayName
            userEmail.text = currentUser.email

            AccountUtils.getCurrentDrive()?.let { currentDrive ->
                driveName.text = currentDrive.name

                driveName.setCompoundDrawables(wrap(driveName.compoundDrawablesRelative.first()).apply {
                    val color = Color.parseColor(currentDrive.preferences.color)
                    setTint(this, color)
                }, null, null, null)

                if (currentDrive.size == 0L) {
                    driveStorageProgress.isInvisible = true
                } else {
                    driveStorageProgress.isVisible = true
                    progressDriveQuota.max = 1_000
                    val progress = (currentDrive.usedSize.toDouble() / currentDrive.size) * 1_000.0
                    progressDriveQuota.progress = progress.toInt()

                    val usedSize = requireContext().formatShortFileSize(currentDrive.usedSize)
                    val totalSize = requireContext().formatShortFileSize(currentDrive.size)
                    textDriveQuota.text = "$usedSize / $totalSize"
                }

                val kSuite = currentDrive.kSuite
                if (kSuite == KSuite.ProFree) {
                    kSuiteProCard.isVisible = true
                    kSuiteProCard.setOnClick { openKSuiteProBottomSheet(kSuite, currentDrive.isAdmin) }
                } else {
                    kSuiteProCard.isGone = true
                }
            }

            userImage.loadAvatar(currentUser)

            if (DriveInfosController.hasSingleDrive(currentUser.id)) {
                driveIcon.isGone = true
            } else {
                driveSwitchContainer.setOnClickListener { safeNavigate(R.id.switchDriveDialog) }
            }

            changeUser.setOnClickListener {
                val switchUserExtra = FragmentNavigatorExtras(changeUser to changeUser.transitionName)
                safeNavigate(R.id.switchUserActivity, null, null, switchUserExtra)
            }

            settings.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToSettingsFragment())
            }

            support.setOnClickListener { requireContext().openSupport() }

            logout.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogStyle)
                    .setTitle(getString(R.string.alertRemoveUserTitle))
                    .setMessage(getString(R.string.alertRemoveUserDescription, currentUser.displayName))
                    .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (UploadFile.getAppSyncSettings()?.userId == currentUser.id) UploadFile.deleteAllSyncFile()
                            AccountUtils.removeUserAndDeleteToken(requireContext(), currentUser)
                        }
                    }
                    .setNegativeButton(R.string.buttonCancel) { _, _ -> }
                    .setCancelable(false).show()
            }
        }

        setupRootPendingFilesIndicator(mainViewModel.pendingUploadsCount, menuUploadFileInProgressView)
    }
}
