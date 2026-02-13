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

import androidx.fragment.app.activityViewModels
import com.infomaniak.drive.MatomoDrive
import com.infomaniak.drive.MatomoDrive.trackPhotoSyncEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.menu.settings.SyncSettingsViewModel

class SyncMediaSelectBottomSheetDialog : SyncSelectBottomSheetDialog() {

    private val syncSettingsViewModel: SyncSettingsViewModel by activityViewModels()

    override val dialogTitle: Int = R.string.syncWifiPicturesTitle
    override val onlyWifiDescription: Int = R.string.syncPhotosOnlyWifiDescription

    override var isOnlyWifiSync: Boolean
        get() = syncSettingsViewModel.onlyWifiSyncMedia.value == true
        set(value) {
            syncSettingsViewModel.onlyWifiSyncMedia.value = value
        }

    override fun trackEvent(name: MatomoDrive.MatomoName) = trackPhotoSyncEvent(name)
}

