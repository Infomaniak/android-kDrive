/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
@file:OptIn(ExperimentalSerializationApi::class)

package com.infomaniak.drive.fcm

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import splitties.init.appCtx

@ConsistentCopyVisibility
@Serializable
data class RegistrationInfo private constructor(
    val token: String,
    val name: String,
    @EncodeDefault
    val os: String = "android",
    @EncodeDefault
    val model: String = android.os.Build.MODEL,
) {
    companion object {
        suspend operator fun invoke(token: String): RegistrationInfo = RegistrationInfo(
            token = token,
            name = getDeviceName()
        )

        private suspend fun getDeviceName() = Dispatchers.IO {
            // Some Xiaomi devices return null for this "device_name" so we need to check this case and not just "blank"
            Settings.Global.getString(appCtx.contentResolver, "device_name")?.takeUnless { it.isEmpty() }
                ?: android.os.Build.MODEL
        }
    }
}
