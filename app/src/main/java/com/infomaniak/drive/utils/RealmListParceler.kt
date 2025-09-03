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
package com.infomaniak.drive.utils

import android.os.Parcel
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.TeamDetails
import io.realm.RealmList
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.parcelize.Parceler

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
                try {
                    list.add(readValue(clazz.classLoader) as T)
                } catch (exception: Exception) {
                    Sentry.withScope { scope ->
                        scope.level = SentryLevel.WARNING
                        Sentry.captureException(exception)
                    }
                }
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

        writeInt(if (realmList == null) 0 else 1)

        if (realmList != null) {
            writeInt(size)
            for (item in realmList) {
                item?.let { writeValue(item) }
            }
        }
    }

    object FileRealmListParceler : RealmListParceler<File> {
        override var clazz: Class<File> = File::class.java
    }

    object FileCategoryRealmListParceler : RealmListParceler<FileCategory> {
        override var clazz: Class<FileCategory> = FileCategory::class.java
    }

    object TeamDetailsRealmListParceler : RealmListParceler<TeamDetails> {
        override var clazz: Class<TeamDetails> = TeamDetails::class.java
    }

    object IntRealmListParceler : RealmListParceler<Int> {
        override var clazz: Class<Int> = Int::class.java
    }

    object StringRealmListParceler : RealmListParceler<String> {
        override var clazz: Class<String> = String::class.java
    }
}
