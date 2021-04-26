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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.LockActivity
import com.infomaniak.drive.ui.LockActivity.Companion.FACE_ID_LOG_TAG
import com.infomaniak.drive.utils.requestCredentials
import com.infomaniak.drive.utils.silentClick
import com.infomaniak.lib.core.utils.UtilsUi
import kotlinx.android.synthetic.main.fragment_base_settings.*

class AppSecuritySettingsFragment : BaseSettingsFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UtilsUi.setupSharedElementTransition(this, R.id.appSecurity, R.id.nestedScrollView)
    }

    private fun onCredentialsSuccessful() {
        Log.i(FACE_ID_LOG_TAG, "success")
        enableFaceIdSwitch.silentClick() // Click that doesn't pass in listener
        AppSettings.appSecurityLock = enableFaceIdSwitch.isChecked
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LockActivity.REQUEST_CODE_SECURITY) {
            if (resultCode == Activity.RESULT_OK) {
                onCredentialsSuccessful()
            } else {
                Log.i(FACE_ID_LOG_TAG, "error")
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title.text = getString(R.string.appSecurityTitle)
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
                requireContext().requestCredentials {
                    onCredentialsSuccessful()
                }
            }
        }
    }
}