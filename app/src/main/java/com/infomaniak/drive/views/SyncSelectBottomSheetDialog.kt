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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPhotoSyncEvent
import com.infomaniak.drive.MatomoDrive.trackSettingsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.FragmentBottomSheetSyncSelectBinding
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.KEY_BACK_ACTION_BOTTOM_SHEET
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.SyncFilesOption
import com.infomaniak.drive.ui.menu.settings.SyncSettingsViewModel
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload

class SyncSelectBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: FragmentBottomSheetSyncSelectBinding by safeBinding()
    private val navArgs by navArgs<SyncSelectBottomSheetDialogArgs>()
    private val syncSettingsViewModel: SyncSettingsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSyncSelectBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        val isWifiSyncOnly = getOnlyWifiSync()
        val syncPermissions = DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@SyncSelectBottomSheetDialog)
        }
        with(binding.syncOnlyWifi) {
            isInactive = !isWifiSyncOnly
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.OnlyWifi) }
        }
        with(binding.syncWithAll) {
            isInactive = isWifiSyncOnly
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.AllData) }
        }
    }

    private fun initView() {
        if (navArgs.isOfflineFilesSetting) {
            binding.selectTitle.setText(R.string.syncWifiSettingsTitle)
            binding.syncOnlyWifi.textDescription = getString(R.string.syncOnlyWifiDescription)
        } else {
            binding.selectTitle.setText(R.string.syncWifiPicturesTitle)
            binding.syncOnlyWifi.textDescription = getString(R.string.syncPhotosOnlyWifiDescription)
        }
    }

    private fun onSelectOption(syncPermissions: DrivePermissions, option: SyncFilesOption) {
        val isOnlyWifiSyncOffline = option == SyncFilesOption.OnlyWifi
        trackEvent(if (isOnlyWifiSyncOffline) MatomoName.SyncOnlyWifi else MatomoName.SyncWifiAndData)
        setOnlyWifiSetting(isOnlyWifiSyncOffline)
        requireContext().launchAllUpload(syncPermissions)
        if (navArgs.isOfflineFilesSetting) setBackNavigationResult(KEY_BACK_ACTION_BOTTOM_SHEET, isOnlyWifiSyncOffline)
        dismiss()
    }

    private fun getOnlyWifiSync(): Boolean {
        return if (navArgs.isOfflineFilesSetting) {
            AppSettings.onlyWifiSyncOffline
        } else {
            syncSettingsViewModel.onlyWifiSyncMedia.value == true
        }
    }

    private fun trackEvent(name: MatomoName) {
        if (navArgs.isOfflineFilesSetting) {
            trackSettingsEvent(name)
        } else {
            trackPhotoSyncEvent(name)
        }
    }

    private fun setOnlyWifiSetting(isOnlyWifiSyncOffline: Boolean) {
        if (navArgs.isOfflineFilesSetting) {
            AppSettings.onlyWifiSyncOffline = isOnlyWifiSyncOffline
        } else {
            syncSettingsViewModel.onlyWifiSyncMedia.value = isOnlyWifiSyncOffline
        }
    }
}
