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
package com.infomaniak.drive.ui.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.activity_no_drive.*
import kotlinx.android.synthetic.main.empty_icon_layout.view.*

class NoDriveActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_drive)

        noDriveIconLayout.icon.setImageResource(R.drawable.ic_no_drive)

        noDriveActionButton.setOnClickListener {
            openUrl(ApiRoutes.orderDrive())
            onBackPressed()
        }
        anotherProfileButton.setOnClickListener { onBackPressed() }
    }
}