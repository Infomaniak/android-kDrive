/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.ViewSwitchSettingsBinding
import com.infomaniak.lib.applock.Utils.silentlyReverseSwitch

class AppSecuritySettingsActivity : AppCompatActivity() {

    private val binding: ViewSwitchSettingsBinding by lazy { ViewSwitchSettingsBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        switchSettingsTitle.text = getString(R.string.appSecurityTitle)
        description.text = getString(R.string.appSecurityDescription)
        image.setImageResource(R.drawable.ic_face_id_edit)
        enableFaceId.isVisible = true
        usersRecyclerView.isGone = true
        addUser.isGone = true

        enableFaceIdSwitch.apply {
            enableFaceId.setOnClickListener { isChecked = !isChecked }
            isChecked = AppSettings.appSecurityLock
            setOnCheckedChangeListener { _, _ ->
                // Reverse switch (before official parameter changed) by silent click
                silentlyReverseSwitch(this) { shouldLock -> AppSettings.appSecurityLock = shouldLock }
            }
        }
    }
}
