/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackSettingsEvent
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.FragmentBottomSheetSyncFilesBinding
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.KEY_BACK_ACTION_BOTTOM_SHEET
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.SyncFilesOption
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately

class SyncFilesBottomSheetDialog : BottomSheetDialogFragment() {

    var binding: FragmentBottomSheetSyncFilesBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSyncFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val syncPermissions = registerDrivePermission()
        with(binding.syncOnlyWifi) {
            isInactive = !AppSettings.onlyWifiSyncOffline
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.OnlyWifi) }
        }
        with(binding.syncWithAll) {
            isInactive = AppSettings.onlyWifiSyncOffline
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.AllData) }
        }
    }

    private fun registerDrivePermission(): DrivePermissions {
        return DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@SyncFilesBottomSheetDialog) { authorized ->
                if (authorized) requireActivity().syncImmediately(isAutomaticTrigger = false)
            }
        }
    }

    private fun onSelectOption(syncPermissions: DrivePermissions, option: SyncFilesOption) {
        val isOnlyWifiSyncOffline = option == SyncFilesOption.OnlyWifi
        trackSettingsEvent(if (isOnlyWifiSyncOffline) MatomoName.SyncOnlyWifi else MatomoName.SyncWifiAndData)
        AppSettings.onlyWifiSyncOffline = isOnlyWifiSyncOffline
        requireContext().launchAllUpload(syncPermissions)
        setBackNavigationResult(KEY_BACK_ACTION_BOTTOM_SHEET, isOnlyWifiSyncOffline)
        dismiss()
    }
}
