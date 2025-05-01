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
import android.view.View
import androidx.annotation.StringRes
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.menu.SyncFilesBottomSheetAdapter

class SyncFilesBottomSheetDialog : SelectBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        selectTitle.setText(R.string.syncWifiSettingsTitle)

        selectRecyclerView.adapter =
            SyncFilesBottomSheetAdapter(syncOptions = SyncFilesOption.ALL_DATA, onItemClicked = {}, context = requireContext())
    }

    companion object {
        enum class SyncFilesOption(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
            ONLY_WIFI(titleRes = R.string.syncOnlyWifiTitle, descriptionRes = R.string.syncOnlyWifiDescription),
            ALL_DATA(titleRes = R.string.syncWifiAndMobileDataTitle, descriptionRes = R.string.syncWifiAndMobileDataDescription),
        }
    }
}
