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
package com.infomaniak.drive.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.FragmentBottomSheetSyncFilesBinding
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.KEY_BACK_ACTION_BOTTOM_SHEET
import com.infomaniak.drive.ui.menu.settings.SettingsFragment.Companion.SyncFilesOption
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.setBackNavigationResult

class SyncFilesBottomSheetDialog : BottomSheetDialogFragment() {

    var binding: FragmentBottomSheetSyncFilesBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSyncFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding.syncOnlyWifi) {
            isActive = AppSettings.onlyWifiSync
            setOnClickListener {
                setBackNavigationResult(KEY_BACK_ACTION_BOTTOM_SHEET, SyncFilesOption.ONLY_WIFI)
            }
        }

        with(binding.syncWithAll) {
            isActive = !AppSettings.onlyWifiSync
            setOnClickListener {
                setBackNavigationResult(KEY_BACK_ACTION_BOTTOM_SHEET, SyncFilesOption.ALL_DATA)
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
