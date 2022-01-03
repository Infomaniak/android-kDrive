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
package com.infomaniak.drive.ui.menu

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat.setTint
import androidx.core.graphics.drawable.DrawableCompat.wrap
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.BuildConfig.SUPPORT_URL
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_menu.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MenuFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                    progressDriveQuota.max = 1000
                    val progress = (currentDrive.usedSize.toDouble() / currentDrive.size) * 1000
                    progressDriveQuota.progress = progress.toInt()

                    val usedSize = FormatterFileSize.formatShortFileSize(requireContext(), currentDrive.usedSize)
                    val size = FormatterFileSize.formatShortFileSize(requireContext(), currentDrive.size)
                    textDriveQuota.text = "$usedSize / $size"
                }
            }

            userImage.loadAvatar(currentUser)
            driveIcon.isVisible = DriveInfosController.getDrives(currentUser.id).size != 1
            driveIcon.setOnClickListener { safeNavigate(R.id.switchDriveDialog) }

            sharedWithMeFiles.isVisible =
                DriveInfosController.getDrives(userId = AccountUtils.currentUserId, sharedWithMe = true).isNotEmpty()

            sharedWithMeFiles.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToSharedWithMeFragment())
            }

            recentChanges.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToRecentChangesFragment())
            }

            offlineFile.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToOfflineFileFragment())
            }

            myShares.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToMySharesFragment())
            }

            pictures.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToMenuPicturesFragment())
            }

            trashbin.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToTrashFragment())
            }

            settings.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToSettingsFragment())
            }

            support.setOnClickListener {
                requireContext().openUrl(SUPPORT_URL)
            }

            changeUser.setOnClickListener {
                val switchUserExtra = FragmentNavigatorExtras(changeUser to changeUser.transitionName)
                safeNavigate(R.id.switchUserActivity, null, null, switchUserExtra)
            }

            logout.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogStyle)
                    .setTitle(getString(R.string.alertRemoveUserTitle))
                    .setMessage(getString(R.string.alertRemoveUserDescription, currentUser.displayName))
                    .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            AccountUtils.removeUser(requireContext(), currentUser)
                        }
                    }
                    .setNegativeButton(R.string.buttonCancel) { _, _ -> }
                    .setCancelable(false).show()
            }
        }

        menuUploadFileInProgress.setUploadFileInProgress(R.string.uploadInProgressTitle) {
            navigateToUploadView(Utils.OTHER_ROOT_ID)
        }
    }

    override fun onResume() {
        super.onResume()
        showPendingFiles()
    }

    private fun showPendingFiles() {
        menuUploadFileInProgress.updateUploadFileInProgress(UploadFile.getCurrentUserPendingUploadsCount())
    }
}