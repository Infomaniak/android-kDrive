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
package com.infomaniak.drive.ui.menu.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.models.user.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application, private val ioDispatcher: CoroutineDispatcher) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication<Application>().applicationContext

    fun disconnectDeletedUser(currentUser: User) {
        viewModelScope.launch(ioDispatcher) {
            AccountUtils.removeUserAndDeleteToken(context, currentUser)
        }
    }
}
