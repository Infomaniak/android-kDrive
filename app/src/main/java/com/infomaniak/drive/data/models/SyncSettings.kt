/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import com.infomaniak.drive.R
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date

open class SyncSettings(
    @PrimaryKey var userId: Int = -1,
    var createDatedSubFolders: Boolean = false,
    var driveId: Int = -1,
    var lastSync: Date = Date(),
    var syncFolder: Int = -1,
    var syncImmediately: Boolean = false,
    var syncInterval: Long = 0,
    var syncVideo: Boolean = false,
    var deleteAfterSync: Boolean = false,
    var onlyWifiSyncMedia: Boolean = false,
) : RealmObject() {

    fun setIntervalType(intervalType: IntervalType) {
        syncImmediately = intervalType.immediately
        syncInterval = intervalType.interval
    }

    fun getIntervalType(): IntervalType {
        return if (syncImmediately) {
            IntervalType.IMMEDIATELY
        } else {
            when (syncInterval) {
                INTERVAL_QUARTER -> IntervalType.ONE_QUARTER
                INTERVAL_HOUR -> IntervalType.ONE_HOUR
                else -> IntervalType.FOUR_HOURS
            }
        }
    }

    enum class IntervalType(val title: Int, val immediately: Boolean, val interval: Long) {
        IMMEDIATELY(R.string.syncSettingsSyncPeriodicityInstantValue, true, INTERVAL_FOUR_HOURS),
        ONE_QUARTER(R.string.syncSettingsSyncPeriodicityOneQuarterValue, false, INTERVAL_QUARTER),
        ONE_HOUR(R.string.syncSettingsSyncPeriodicityOneHourValue, false, INTERVAL_HOUR),
        FOUR_HOURS(R.string.syncSettingsSyncPeriodicityFourHoursValue, false, INTERVAL_FOUR_HOURS)
    }

    companion object {
        private const val INTERVAL_HOUR: Long = 60 * 60L
        private const val INTERVAL_QUARTER: Long = 15 * 60L
        private const val INTERVAL_FOUR_HOURS: Long = 4 * 60 * 60L
    }

    enum class SavePicturesDate(val shortTitle: Int, val title: Int) {
        SINCE_NOW(R.string.syncSettingsSaveDateNowValue, R.string.syncSettingsSaveDateNowValue2),
        SINCE_FOREVER(R.string.syncSettingsSaveDateAllPictureValue, R.string.syncSettingsSaveDateAllPictureValue),
        SINCE_DATE(R.string.syncSettingsSaveDateFromDateValue, R.string.syncSettingsSaveDateFromDateValue2)
    }
}
