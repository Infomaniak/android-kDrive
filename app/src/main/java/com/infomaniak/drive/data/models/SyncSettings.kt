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

import android.os.Build
import com.infomaniak.drive.R
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

open class SyncSettings(
    @PrimaryKey var userId: Int = -1,
    var driveId: Int = -1,
    var lastSync: Date = Date(),
    var syncImmediately: Boolean = false,
    var syncInterval: Long = 0,
    var syncFolder: Int = -1,
    var syncPicture: Boolean = false,
    var syncScreenshot: Boolean = false,
    var syncVideo: Boolean = false
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

    enum class IntervalType(val title: Int, val immediately: Boolean, val interval: Long, val minAndroidSdk: Int) {
        IMMEDIATELY(R.string.syncSettingsSyncPeriodicityInstantValue, true, INTERVAL_FOUR_HOURS, Build.VERSION_CODES.LOLLIPOP),
        ONE_QUARTER(R.string.syncSettingsSyncPeriodicityOneQuarterValue, false, INTERVAL_QUARTER, Build.VERSION_CODES.N),
        ONE_HOUR(R.string.syncSettingsSyncPeriodicityOneHourValue, false, INTERVAL_HOUR, Build.VERSION_CODES.LOLLIPOP),
        FOUR_HOURS(R.string.syncSettingsSyncPeriodicityFourHoursValue, false, INTERVAL_FOUR_HOURS, Build.VERSION_CODES.LOLLIPOP)
    }

    companion object {
        private const val INTERVAL_HOUR: Long = 60 * 60
        private const val INTERVAL_QUARTER: Long = 15 * 60
        private const val INTERVAL_FOUR_HOURS: Long = 4 * 60 * 60
    }
}