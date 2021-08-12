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
package com.infomaniak.drive.utils

import android.os.Parcel
import com.infomaniak.drive.data.models.File
import io.realm.RealmList
import kotlinx.android.parcel.Parceler

interface RealmListParceler<T> : Parceler<RealmList<T>?> {

    var clazz: Class<T>

    override fun create(parcel: Parcel): RealmList<T>? = parcel.readRealmList(clazz)

    override fun RealmList<T>?.write(parcel: Parcel, flags: Int) {
        parcel.writeRealmList(this)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Parcel.readRealmList(clazz: Class<T>): RealmList<T>? = when {
        readInt() > 0 -> RealmList<T>().also { list ->
            repeat(readInt()) {
                list.add(readValue(clazz.classLoader) as T)
            }
        }
        else -> null
    }

    private fun <T> Parcel.writeRealmList(realmList: RealmList<T>?) {
        val size = try {
            realmList?.size ?: 0
        } catch (exception: Exception) {
            exception.printStackTrace()
            return
        }

        writeInt(
            when (realmList) {
                null -> 0
                else -> 1
            }
        )
        if (realmList != null) {
            writeInt(size)
            for (t in realmList) {
                writeValue(t)
            }
        }
    }

    object FileRealmListParceler : RealmListParceler<File> {
        override var clazz: Class<File> = File::class.java
    }

    object IntRealmListParceler : RealmListParceler<Int> {
        override var clazz: Class<Int> = Int::class.java
    }
}