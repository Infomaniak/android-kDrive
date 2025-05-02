/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.SyncSettings.SavePicturesDate
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog.Companion.manufacturerWarning
import com.infomaniak.drive.ui.home.RootFileTreeCategory
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.networking.AccessTokenUsageInterceptor.ApiCallRecord
import com.infomaniak.lib.core.utils.SharedValues
import com.infomaniak.lib.core.utils.sharedValue
import com.infomaniak.lib.core.utils.sharedValueWithOrdinal
import com.infomaniak.lib.core.utils.transaction

class UiSettings(context: Context) : SharedValues {

    override val sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)!!

    fun removeUiSettings() = sharedPreferences.transaction { clear() }

    //region Save External
    fun getSaveExternalFilesPref() = SaveExternalFilesData(
        userId = saveExternalFilesUserId,
        driveId = saveExternalFilesDriveId,
        folderId = if (saveExternalFilesFolderId >= Utils.ROOT_ID) saveExternalFilesFolderId else null,
    )

    fun setSaveExternalFilesPref(userId: Int, driveId: Int, folderId: Int) {
        saveExternalFilesUserId = userId
        saveExternalFilesDriveId = driveId
        saveExternalFilesFolderId = folderId
    }

    private var saveExternalFilesUserId by sharedValue("saveExternalFilesPref_userId", -1)
    private var saveExternalFilesDriveId by sharedValue("saveExternalFilesPref_driveId", -1)
    private var saveExternalFilesFolderId by sharedValue("saveExternalFilesPref_folderId", -1)
    //endregion

    var bottomNavigationSelectedItem by sharedValue("bottomNavigationSelectedItem", R.id.hostFragment)
    var lastVisitedRootFileTreeCategory by sharedValueWithOrdinal<RootFileTreeCategory>("lastVisitedRootFileTreeCategory", null)
    var hasDisplayedSyncDialog by sharedValue("hasDisplayedSyncDialog", false)
    var listMode by sharedValue("listMode", true)
    var mustDisplayBatteryDialog by sharedValue("mustDisplayBatteryDialog", manufacturerWarning)
    var nightMode by sharedValue("nightMode", MODE_NIGHT_FOLLOW_SYSTEM)
    var recentSearches by sharedValue("recentSearches", emptyList())
    var sortType by sharedValue("sortType", SortType.NAME_AZ)
    var accessTokenApiCallRecord by sharedValue<ApiCallRecord>("accessTokenApiCallRecord", null)
    var syncSettingsDate by sharedValue("syncSettingsDate", SavePicturesDate.SINCE_NOW)

    data class SaveExternalFilesData(
        val userId: Int,
        val driveId: Int,
        val folderId: Int?,
    )

    companion object {

        private const val SHARED_PREFS_NAME = "UISettings"
    }
}
