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
package com.infomaniak.drive.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.infomaniak.drive.MatomoDrive.trackAccountEvent
import com.infomaniak.drive.databinding.ViewSwitchSettingsBinding
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.ui.menu.UserAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.models.user.User

class SwitchUserActivity : AppCompatActivity() {

    private val binding: ViewSwitchSettingsBinding by lazy { ViewSwitchSettingsBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        AccountUtils.getAllUsers().observe(this@SwitchUserActivity) { users ->
            usersRecyclerView.adapter = UserAdapter(users as ArrayList<User>) { user ->
                trackAccountEvent("switch")
                AccountUtils.currentUser = user
                AccountUtils.currentDriveId = -1
                AccountUtils.reloadApp?.invoke(bundleOf())
            }
        }

        addUser.setOnClickListener {
            trackAccountEvent("add")
            startActivity(Intent(this@SwitchUserActivity, LoginActivity::class.java))
        }
    }
}
