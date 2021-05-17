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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.LockActivity
import com.infomaniak.drive.ui.LockActivity.Companion.FACE_ID_LOG_TAG
import com.infomaniak.drive.utils.requestCredentials
import com.infomaniak.drive.utils.silentClick
import kotlinx.android.synthetic.main.view_switch_settings.*

class AppSecuritySettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_switch_settings)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        switchSettingsTitle.text = getString(R.string.appSecurityTitle)
        description.text = getString(R.string.appSecurityDescription)
        image.setImageResource(R.drawable.ic_face_id_edit)
        enableFaceId.visibility = View.VISIBLE
        usersRecyclerView.visibility = View.GONE
        addUser.visibility = View.GONE

        enableFaceId.setOnClickListener { enableFaceIdSwitch.isChecked = !enableFaceIdSwitch.isChecked }
        enableFaceIdSwitch.isChecked = AppSettings.appSecurityLock
        enableFaceIdSwitch.setOnCheckedChangeListener { _, _ ->
            // Reverse switch (before official parameter changed) by silent click
            if (enableFaceIdSwitch.tag == null) {
                enableFaceIdSwitch.silentClick()
                requestCredentials {
                    onCredentialsSuccessful()
                }
            }
        }
    }

    private fun onCredentialsSuccessful() {
        Log.i(FACE_ID_LOG_TAG, "success")
        enableFaceIdSwitch.silentClick() // Click that doesn't pass in listener
        AppSettings.appSecurityLock = enableFaceIdSwitch.isChecked
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == LockActivity.REQUEST_CODE_SECURITY) {
            if (resultCode == Activity.RESULT_OK) {
                onCredentialsSuccessful()
            } else {
                Log.i(FACE_ID_LOG_TAG, "error")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}