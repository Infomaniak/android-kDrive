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
package com.infomaniak.drive.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.login.LoginActivity
import com.infomaniak.drive.ui.menu.UserAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.models.User
import kotlinx.android.synthetic.main.fragment_base_settings.*

class SwitchUserActivity : AppCompatActivity() {

    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_base_settings)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        AccountUtils.getAllUsers().observe(this) { users ->
            usersRecyclerView.adapter = UserAdapter(users as ArrayList<User>) { user ->
                AccountUtils.currentUser = user
                AccountUtils.reloadApp?.invoke()
            }
        }

        addUser.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}