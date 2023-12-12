/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import com.google.gson.reflect.TypeToken
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog.Companion.manufacturerWarning
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.utils.transaction

class UiSettings(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    fun removeUiSettings() = sharedPreferences.transaction { clear() }

    fun getSaveExternalFilesPref(): SaveExternalFilesData {
        val userId = sharedPreferences.getInt(SAVE_EXTERNAL_FILES_USER_ID_KEY, DEFAULT_SAVE_EXTERNAL_FILES_USER_ID)
        val driveId = sharedPreferences.getInt(SAVE_EXTERNAL_FILES_DRIVE_ID_KEY, DEFAULT_SAVE_EXTERNAL_FILES_DRIVE_ID)
        val folderId = sharedPreferences.getInt(SAVE_EXTERNAL_FILES_FOLDER_ID_KEY, DEFAULT_SAVE_EXTERNAL_FILES_FOLDER_ID)

        return SaveExternalFilesData(userId, driveId, if (folderId >= Utils.ROOT_ID) folderId else null)
    }

    fun setSaveExternalFilesPref(userId: Int, driveId: Int, folderId: Int) {
        sharedPreferences.transaction {
            putInt(SAVE_EXTERNAL_FILES_USER_ID_KEY, userId)
            putInt(SAVE_EXTERNAL_FILES_DRIVE_ID_KEY, driveId)
            putInt(SAVE_EXTERNAL_FILES_FOLDER_ID_KEY, folderId)
        }
    }

    var bottomNavigationSelectedItem: Int
        get() = sharedPreferences.getInt(BOTTOM_NAVIGATION_SELECTED_ITEM_KEY, R.id.hostFragment)
        set(value) = sharedPreferences.transaction { putInt(BOTTOM_NAVIGATION_SELECTED_ITEM_KEY, value) }

    var hasDisplayedSyncDialog: Boolean
        get() = sharedPreferences.getBoolean(HAS_DISPLAYED_SYNC_DIALOG_KEY, DEFAULT_HAS_DISPLAYED_SYNC_DIALOG)
        set(value) = sharedPreferences.transaction { putBoolean(HAS_DISPLAYED_SYNC_DIALOG_KEY, value) }

    var lastHomeSelectedTab: Int
        get() = sharedPreferences.getInt(LAST_HOME_SELECTED_TAB_KEY, DEFAULT_LAST_HOME_SELECTED_TAB)
        set(value) = sharedPreferences.transaction { putInt(LAST_HOME_SELECTED_TAB_KEY, value) }

    var listMode: Boolean
        get() = sharedPreferences.getBoolean(LIST_MODE_KEY, DEFAULT_LIST_MODE)
        set(value) = sharedPreferences.transaction { putBoolean(LIST_MODE_KEY, value) }

    var mustDisplayBatteryDialog: Boolean
        get() = sharedPreferences.getBoolean(MUST_DISPLAY_BATTERY_DIALOG_KEY, DEFAULT_MUST_DISPLAY_BATTERY_DIALOG)
        set(value) = sharedPreferences.transaction { putBoolean(MUST_DISPLAY_BATTERY_DIALOG_KEY, value) }

    var nightMode: Int
        get() = sharedPreferences.getInt(NIGHT_MODE_KEY, DEFAULT_NIGHT_MODE)
        set(value) = sharedPreferences.transaction { putInt(NIGHT_MODE_KEY, value) }

    var recentSearches: List<String>
        get() = ApiController.gson.fromJson(
            sharedPreferences.getString(RECENT_SEARCHES_KEY, DEFAULT_RECENT_SEARCHES),
            object : TypeToken<List<String>>() {}.type,
        ) ?: emptyList()
        set(value) = sharedPreferences.transaction { putString(RECENT_SEARCHES_KEY, ApiController.gson.toJson(value)) }

    var sortType: SortType
        get() = when (sharedPreferences.getString(SORT_TYPE_KEY, DEFAULT_SORT_TYPE)) {
            SortType.NAME_AZ.name -> SortType.NAME_AZ
            SortType.NAME_ZA.name -> SortType.NAME_ZA
            SortType.OLDER.name -> SortType.OLDER
            SortType.RECENT.name -> SortType.RECENT
            SortType.OLDEST_ADDED.name -> SortType.OLDEST_ADDED
            SortType.MOST_RECENT_ADDED.name -> SortType.MOST_RECENT_ADDED
            SortType.BIGGER.name -> SortType.BIGGER
            SortType.SMALLER.name -> SortType.SMALLER
            //File.SortType.EXTENSION.name -> File.SortType.EXTENSION
            else -> SortType.NAME_AZ
        }
        set(value) = sharedPreferences.transaction { putString(SORT_TYPE_KEY, value.name) }

    var updateLater: Boolean
        get() = sharedPreferences.getBoolean(UPDATE_LATER_KEY, DEFAULT_UPDATE_LATER)
        set(value) = sharedPreferences.transaction { putBoolean(UPDATE_LATER_KEY, value) }

    data class SaveExternalFilesData(
        val userId: Int,
        val driveId: Int,
        val folderId: Int?,
    )

    companion object {

        private const val SHARED_PREFS_NAME = "UISettings"

        //region Default values
        private const val DEFAULT_SAVE_EXTERNAL_FILES_USER_ID = -1
        private const val DEFAULT_SAVE_EXTERNAL_FILES_DRIVE_ID = -1
        private const val DEFAULT_SAVE_EXTERNAL_FILES_FOLDER_ID = -1
        private const val DEFAULT_HAS_DISPLAYED_SYNC_DIALOG = false
        private const val DEFAULT_LAST_HOME_SELECTED_TAB = 0
        private const val DEFAULT_LIST_MODE = true
        private val DEFAULT_MUST_DISPLAY_BATTERY_DIALOG = manufacturerWarning
        private const val DEFAULT_NIGHT_MODE = MODE_NIGHT_FOLLOW_SYSTEM
        private val DEFAULT_RECENT_SEARCHES = null
        private val DEFAULT_SORT_TYPE = SortType.NAME_AZ.name
        private const val DEFAULT_UPDATE_LATER = false
        //endregion

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
        //endregion
    }
}
