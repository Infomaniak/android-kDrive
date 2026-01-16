/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPhotoSyncEvent
import com.infomaniak.drive.databinding.FragmentBottomSheetSyncMediaBinding
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.SyncFilesOption
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately

class SyncMediaBottomSheetDialog : BottomSheetDialogFragment() {
    private val syncSettingsViewModel: SyncSettingsViewModel by activityViewModels()

    var binding: FragmentBottomSheetSyncMediaBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSyncMediaBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val syncPermissions = registerDrivePermission()
        val isOnlyWifiSyncMedia = syncSettingsViewModel.onlyWifiSyncMedia.value == true
        with(binding.syncOnlyWifi) {
            isInactive = !isOnlyWifiSyncMedia
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.ONLY_WIFI) }
        }
        with(binding.syncWithAll) {
            isInactive = isOnlyWifiSyncMedia
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.ALL_DATA) }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun registerDrivePermission(): DrivePermissions {
        return DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@SyncMediaBottomSheetDialog) { authorized -> if (authorized) requireActivity().syncImmediately() }
        }
    }

    private fun onSelectOption(syncPermissions: DrivePermissions, option: SyncFilesOption) {
        val isOnlyWifiSyncOffline = option == SyncFilesOption.ONLY_WIFI
        trackPhotoSyncEvent(name = if (isOnlyWifiSyncOffline) MatomoName.SyncOnlyWifi else MatomoName.SyncWifiAndData)
        syncSettingsViewModel.onlyWifiSyncMedia.value = isOnlyWifiSyncOffline
        requireContext().launchAllUpload(syncPermissions)
        dismiss()
    }
}
