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

import android.graphics.Color
import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import io.realm.RealmObject
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Team(
    override val id: Int,
    val name: String,
    val color: Int,
    val users: List<Int>,
    val details: List<TeamDetails>,
    @SerializedName("right") override var permission: String,
) : Parcelable, Shareable {

    fun isAllUsers(): Boolean = id == 0

    fun getParsedColor(): Int {
        return Color.parseColor(
            if (isAllUsers()) "#4051b5" else
                when (this.color) {
                    0 -> "#F44336"
                    1 -> "#E91E63"
                    2 -> "#9C26B0"
                    3 -> "#673AB7"
                    4 -> "#4051B5"
                    5 -> "#4BAF50"
                    6 -> "#009688"
                    7 -> "#00BCD4"
                    8 -> "#02A9F4"
                    9 -> "#2196F3"
                    10 -> "#8BC34A"
                    11 -> "#CDDC3A"
                    12 -> "#FFC10A"
                    13 -> "#FF9802"
                    14 -> "#607D8B"
                    15 -> "#9E9E9E"
                    16 -> "#795548"
                    else -> "#E91E63"
                }
        )
    }
}