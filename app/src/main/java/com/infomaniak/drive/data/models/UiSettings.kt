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
package com.infomaniak.drive.data.models

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog.Companion.manufacturerWarning
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.api.ApiController

class UiSettings(private val context: Context) {

    private fun getUiSettings(): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun removeUiSettings() {
        with(getUiSettings().edit()) {
            clear()
            apply()
        }
    }

    fun getSaveExternalFilesPref(): SaveExternalFilesData {
        val uiSettings = getUiSettings()
        val userId = uiSettings.getInt(SAVE_EXTERNAL_FILES_USER_ID_KEY, -1)
        val driveId = uiSettings.getInt(SAVE_EXTERNAL_FILES_DRIVE_ID_KEY, -1)
        val folderId = uiSettings.getInt(SAVE_EXTERNAL_FILES_FOLDER_ID_KEY, -1)

        return SaveExternalFilesData(userId, driveId, if (folderId >= Utils.ROOT_ID) folderId else null)
    }

    fun setSaveExternalFilesPref(userId: Int, driveId: Int, folderId: Int) {
        with(getUiSettings().edit()) {
            putInt(SAVE_EXTERNAL_FILES_USER_ID_KEY, userId)
            putInt(SAVE_EXTERNAL_FILES_DRIVE_ID_KEY, driveId)
            putInt(SAVE_EXTERNAL_FILES_FOLDER_ID_KEY, folderId)
            apply()
        }
    }

    var bottomNavigationSelectedItem: Int
        get() = getUiSettings().getInt(BOTTOM_NAVIGATION_SELECTED_ITEM_KEY, R.id.hostFragment)
        set(value) {
            with(getUiSettings().edit()) {
                putInt(BOTTOM_NAVIGATION_SELECTED_ITEM_KEY, value)
                apply()
            }
        }

    var hasDisplayedSyncDialog: Boolean
        get() = getUiSettings().getBoolean(HAS_DISPLAYED_SYNC_DIALOG_KEY, false)
        set(value) {
            with(getUiSettings().edit()) {
                putBoolean(HAS_DISPLAYED_SYNC_DIALOG_KEY, value)
                apply()
            }
        }

    var lastHomeSelectedTab: Int
        get() = getUiSettings().getInt(LAST_HOME_SELECTED_TAB_KEY, 0)
        set(value) {
            with(getUiSettings().edit()) {
                putInt(LAST_HOME_SELECTED_TAB_KEY, value)
                apply()
            }
        }

    var listMode: Boolean
        get() = getUiSettings().getBoolean(LIST_MODE_KEY, true)
        set(value) {
            with(getUiSettings().edit()) {
                putBoolean(LIST_MODE_KEY, value)
                apply()
            }
        }

    var mustDisplayBatteryDialog: Boolean
        get() = getUiSettings().getBoolean(MUST_DISPLAY_BATTERY_DIALOG_KEY, manufacturerWarning)
        set(value) {
            with(getUiSettings().edit()) {
                putBoolean(MUST_DISPLAY_BATTERY_DIALOG_KEY, value)
                apply()
            }
        }

    var nightMode: Int
        get() = getUiSettings().getInt(NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) {
            with(getUiSettings().edit()) {
                putInt(NIGHT_MODE_KEY, value)
                apply()
            }
        }

    var recentSearches: List<String>
        get() = ApiController.gson.fromJson(
            getUiSettings().getString(RECENT_SEARCHES_KEY, null),
            object : TypeToken<List<String>>() {}.type,
        ) ?: emptyList()
        set(value) {
            with(getUiSettings().edit()) {
                putString(RECENT_SEARCHES_KEY, ApiController.gson.toJson(value))
                apply()
            }
        }

    var sortType: File.SortType
        get() = when (getUiSettings().getString(SORT_TYPE_KEY, File.SortType.NAME_AZ.name)) {
            File.SortType.NAME_AZ.name -> File.SortType.NAME_AZ
            File.SortType.NAME_ZA.name -> File.SortType.NAME_ZA
            File.SortType.OLDER.name -> File.SortType.OLDER
            File.SortType.RECENT.name -> File.SortType.RECENT
            File.SortType.OLDEST_ADDED.name -> File.SortType.OLDEST_ADDED
            File.SortType.MOST_RECENT_ADDED.name -> File.SortType.MOST_RECENT_ADDED
            File.SortType.BIGGER.name -> File.SortType.BIGGER
            File.SortType.SMALLER.name -> File.SortType.SMALLER
            //File.SortType.EXTENSION.name -> File.SortType.EXTENSION
            else -> File.SortType.NAME_AZ
        }
        set(value) {
            with(getUiSettings().edit()) {
                putString(SORT_TYPE_KEY, value.name)
                apply()
            }
        }

    var updateLater: Boolean
        get() = getUiSettings().getBoolean(UPDATE_LATER_KEY, false)
        set(value) {
            with(getUiSettings().edit()) {
                putBoolean(UPDATE_LATER_KEY, value)
                apply()
            }
        }

    data class SaveExternalFilesData(
        val userId: Int,
        val driveId: Int,
        val folderId: Int?,
    )

    companion object {

        private const val SHARED_PREFS_NAME = "UISettings"

        //region Keys
        private const val SAVE_EXTERNAL_FILES_USER_ID_KEY = "saveExternalFilesPref_userId"
        private const val SAVE_EXTERNAL_FILES_DRIVE_ID_KEY = "saveExternalFilesPref_driveId"
        private const val SAVE_EXTERNAL_FILES_FOLDER_ID_KEY = "saveExternalFilesPref_folderId"
        private const val BOTTOM_NAVIGATION_SELECTED_ITEM_KEY = "bottomNavigationSelectedItem"
        private const val HAS_DISPLAYED_SYNC_DIALOG_KEY = "hasDisplayedSyncDialog"
        private const val LAST_HOME_SELECTED_TAB_KEY = "lastHomeSelectedTab"
        private const val LIST_MODE_KEY = "listMode"
        private const val MUST_DISPLAY_BATTERY_DIALOG_KEY = "mustDisplayBatteryDialog"
        private const val NIGHT_MODE_KEY = "nightMode"
        private const val RECENT_SEARCHES_KEY = "recentSearches"
        private const val SORT_TYPE_KEY = "sortType"
        private const val UPDATE_LATER_KEY = "updateLater"
        //endRegion
    }
}
