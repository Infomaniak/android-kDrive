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

import android.os.Parcelable
import androidx.core.graphics.toColorInt
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.RealmListParceler.IntRealmListParceler
import com.infomaniak.drive.utils.RealmListParceler.TeamDetailsRealmListParceler
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

@Parcelize
open class Team(
    /** Team File Access */
    @PrimaryKey override var id: Int = -1,
    var name: String = "",
    override var right: String = "",
    var color: Int = -1,
    var status: String = "",
    /** Only team */
    var users: @WriteWith<IntRealmListParceler> RealmList<Int> = RealmList(),
    var details: @WriteWith<TeamDetailsRealmListParceler> RealmList<TeamDetails> = RealmList(),
) : RealmObject(), Parcelable, Shareable, Comparable<Team> {

    fun isAllUsers(): Boolean = id == 0

    fun usersCount(drive: Drive): Int {
        val detail = details.firstOrNull { it.driveId == drive.id }
        return detail?.usersCount ?: users.filter { drive.users.internal.contains(it) }.size
    }

    fun getParsedColor(): Int {
        val parsedColor = if (isAllUsers()) {
            "#4051b5"
        } else {
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
        }
        return parsedColor.toColorInt()
    }

    override fun compareTo(other: Team): Int {
        return when {
            isAllUsers() -> -1
            other.isAllUsers() -> 1
            else -> name.compareTo(other.name)
        }
    }
}
