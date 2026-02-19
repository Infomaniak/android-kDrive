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
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.databinding.FragmentBottomSheetSyncSelectBinding
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.SyncFilesOption
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload

abstract class SyncSelectBottomSheetDialog : BottomSheetDialogFragment() {

    abstract val dialogTitle: Int
    abstract val onlyWifiDescription: Int
    abstract var isOnlyWifiSync: Boolean
    private var binding: FragmentBottomSheetSyncSelectBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSyncSelectBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val syncPermissions = DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@SyncSelectBottomSheetDialog)
        }
        binding.initViews(syncPermissions)
    }

    private fun FragmentBottomSheetSyncSelectBinding.initViews(syncPermissions: DrivePermissions) {
        selectTitle.setText(dialogTitle)
        with(syncOnlyWifi) {
            textDescription = getString(onlyWifiDescription)
            isInactive = !isOnlyWifiSync
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.OnlyWifi) }
        }
        with(syncWithAll) {
            isInactive = isOnlyWifiSync
            setOnClickListener { onSelectOption(syncPermissions, SyncFilesOption.AllData) }
        }
    }

    private fun onSelectOption(syncPermissions: DrivePermissions, option: SyncFilesOption) {
        isOnlyWifiSync = option == SyncFilesOption.OnlyWifi
        trackEvent(if (isOnlyWifiSync) MatomoName.SyncOnlyWifi else MatomoName.SyncWifiAndData)
        requireContext().launchAllUpload(syncPermissions)
        dismiss()
    }

    abstract fun trackEvent(name: MatomoName)

}
