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
import android.view.View.*
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat.setTint
import androidx.core.graphics.drawable.DrawableCompat.wrap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FormatterFileSize
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_menu.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MenuFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

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
                    driveStorageProgress.visibility = INVISIBLE
                } else {
                    driveStorageProgress.visibility = VISIBLE
                    progressDriveQuota.max = 1000
                    val progress = (currentDrive.usedSize.toDouble() / currentDrive.size) * 1000
                    progressDriveQuota.progress = progress.toInt()

                    val usedSize = FormatterFileSize.formatShortFileSize(requireContext(), currentDrive.usedSize)
                    val size = FormatterFileSize.formatShortFileSize(requireContext(), currentDrive.size)
                    textDriveQuota.text = "$usedSize / $size"
                }
            }

            userImage.loadAvatar(currentUser)

            driveIcon.setOnClickListener {
                safeNavigate(
                    MenuFragmentDirections.actionMenuFragmentToHomeFragment(true)
                )
            }

            sharedWithMeFiles.visibility =
                if (DriveInfosController.getDrives(userId = AccountUtils.currentUserId, sharedWithMe = true).isEmpty()) GONE
                else VISIBLE

            sharedWithMeFiles.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToSharedWithMeFragment())
            }

            offlineFile.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToOfflineFileFragment())
            }

            myShares.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToMySharesFragment())
            }

            pictures.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToPicturesFragment())
            }

            trashbin.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToTrashFragment())
            }

            settings.setOnClickListener {
                safeNavigate(MenuFragmentDirections.actionMenuFragmentToSettingsFragment())
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
    }
}