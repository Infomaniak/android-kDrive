/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class SyncAfterMigrationBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title.setText(R.string.migratePhotoSyncSettingsTitle)
        description.setText(R.string.migratePhotoSyncSettingsDescription)

        illu.layoutParams.height = 80.toPx()
        illu.layoutParams.width = 80.toPx()
        illu.setImageResource(R.drawable.ic_info)

        actionButton.setText(R.string.buttonConfigure)
        actionButton.setOnClickListener {
            safeNavigate(R.id.syncSettingsActivity)
            dismiss()
        }
    }
}