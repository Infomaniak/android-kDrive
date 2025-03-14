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
package com.infomaniak.drive.ui.menu.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.data.models.SyncSettings.IntervalType
import com.infomaniak.drive.data.models.SyncSettings.SavePicturesDate
import java.util.Date

class SyncSettingsViewModel : ViewModel() {
    val customDate = MutableLiveData<Date>()
    val saveOldPictures = MutableLiveData<SavePicturesDate>()
    val syncIntervalType = MutableLiveData<IntervalType>()
    val syncFolderId = MutableLiveData<Int?>()

    fun init(intervalTypeValue: IntervalType, syncFolderId: Int?, savePicturesDate: SavePicturesDate) {
        this.syncFolderId.value = syncFolderId
        syncIntervalType.value = intervalTypeValue
        saveOldPictures.value = savePicturesDate
    }
}
