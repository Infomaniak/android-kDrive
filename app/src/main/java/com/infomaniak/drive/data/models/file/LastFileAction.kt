/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.file

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileActivityType
import kotlinx.parcelize.Parcelize

@Parcelize
data class LastFileAction(
    @SerializedName("file_id")
    val fileId: Int,
    @SerializedName("last_action")
    val lastAction: FileActivityType? = null,
    @SerializedName("last_action_at")
    val lastActionAt: Long? = null,
    val file: File? = null
) : Parcelable
