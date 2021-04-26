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
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Tag(
    override val id: Int,
    val name: String,
    val color: Int,
    @SerializedName("right") override var permission: String
) : Parcelable, Shareable {

    fun isAllDriveUsersTag(): Boolean = id == 0

    @JvmName("getParsedColor")
    fun getColor(): Int {
        return Color.parseColor(
            when (color) {
                0 -> "#4051B5"
                1 -> "#30ABFF"
                2 -> "#ED2C6E"
                3 -> "#FFB11B"
                4 -> "#029688"
                5 -> "#7974B4"
                6 -> "#3CB572"
                7 -> "#05C2E7"
                8 -> "#D9283A"
                9 -> "#3990BB"
                else -> "#30ABFF"
            }
        )
    }
}