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
package com.infomaniak.drive.data.models

import android.content.Context
import android.content.SharedPreferences
import com.infomaniak.drive.R

class UISettings(val context: Context) {

    private fun getUISettings(): SharedPreferences {
        return context.getSharedPreferences("UISettings", Context.MODE_PRIVATE)
    }

    fun removeUiSettings() {
        with(getUISettings().edit()) {
            clear()
            apply()
        }
    }

    fun getSaveExternalFilesPref(): Triple<Int, Int, Int> {
        val uiSettings = getUISettings()
        val userId = uiSettings.getInt("saveExternalFilesPref_userId", -1)
        val driveId = uiSettings.getInt("saveExternalFilesPref_driveId", -1)
        val folderId = uiSettings.getInt("saveExternalFilesPref_folderId", -1)
        return Triple(userId, driveId, folderId)
    }

    fun setSaveExternalFilesPref(userId: Int, driveId: Int, folderId: Int) {
        with(getUISettings().edit()) {
            putInt("saveExternalFilesPref_userId", userId)
            putInt("saveExternalFilesPref_driveId", driveId)
            putInt("saveExternalFilesPref_folderId", folderId)
            apply()
        }
    }

    var bottomNavigationSelectedItem: Int
        get() = getUISettings().getInt("bottomNavigationSelectedItem", R.id.hostFragment)
        set(value) {
            with(getUISettings().edit()) {
                putInt("bottomNavigationSelectedItem", value)
                apply()
            }
        }

    var listMode: Boolean
        get() = getUISettings().getBoolean("listMode", true)
        set(value) {
            with(getUISettings().edit()) {
                putBoolean("listMode", value)
                apply()
            }
        }

    var sortType: File.SortType
        get() {
            return when (getUISettings().getString("sortType", File.SortType.NAME_AZ.name)) {
                File.SortType.NAME_AZ.name -> File.SortType.NAME_AZ
                File.SortType.NAME_ZA.name -> File.SortType.NAME_ZA
                File.SortType.OLDER.name -> File.SortType.OLDER
                File.SortType.RECENT.name -> File.SortType.RECENT
                File.SortType.BIGGER.name -> File.SortType.BIGGER
                File.SortType.SMALLER.name -> File.SortType.SMALLER
                File.SortType.EXTENSION.name -> File.SortType.EXTENSION
                else -> File.SortType.NAME_AZ
            }
        }
        set(value) {
            with(getUISettings().edit()) {
                putString("sortType", value.name)
                apply()
            }
        }

    var updateLater: Boolean
        get() = getUISettings().getBoolean("updateLater", false)
        set(value) {
            with(getUISettings().edit()) {
                putBoolean("updateLater", value)
                apply()
            }
        }
}