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
package com.infomaniak.drive.data.models.drive

import androidx.collection.ArrayMap
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.data.models.DriveUser

data class DriveInfo(
    val drives: DriveList = DriveList(),
    val users: ArrayMap<Int, DriveUser> = ArrayMap()
) {
    data class DriveList(
        val main: ArrayList<Drive> = ArrayList(),
        @SerializedName("shared_with_me")
        val sharedWithMe: ArrayList<Drive> = ArrayList()
    )
}