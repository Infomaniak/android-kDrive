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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.LockActivity.Companion.FACE_ID_LOG_TAG
import com.infomaniak.drive.utils.requestCredentials
import kotlinx.android.synthetic.main.view_switch_settings.*

class AppSecuritySettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_switch_settings)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        switchSettingsTitle.text = getString(R.string.appSecurityTitle)
        description.text = getString(R.string.appSecurityDescription)
        image.setImageResource(R.drawable.ic_face_id_edit)
        enableFaceId.isVisible = true
        usersRecyclerView.isGone = true
        addUser.isGone = true

        enableFaceId.setOnClickListener { enableFaceIdSwitch.isChecked = !enableFaceIdSwitch.isChecked }
        enableFaceIdSwitch.isChecked = AppSettings.appSecurityLock
        enableFaceIdSwitch.setOnCheckedChangeListener { _, _ ->
            // Reverse switch (before official parameter changed) by silent click
            if (enableFaceIdSwitch.tag == null) {
                enableFaceIdSwitch.silentClick()
                requestCredentials { onCredentialsSuccessful() }
            }
        }
    }

    private fun onCredentialsSuccessful() {
        Log.i(FACE_ID_LOG_TAG, "success")
        enableFaceIdSwitch.silentClick() // Click that doesn't pass in listener
        AppSettings.appSecurityLock = enableFaceIdSwitch.isChecked
    }

    private fun CompoundButton.silentClick() {
        tag = true
        performClick()
        tag = null
    }
}