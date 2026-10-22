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
package com.infomaniak.drive.data.models.file

import android.os.Parcelable
import com.infomaniak.drive.utils.serialization.RealmStringListSerializer
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@RealmClass(embedded = true)
open class FileConversion(
    @SerialName("when_downloading")
    var whenDownload: Boolean = false,
    @Serializable(with = RealmStringListSerializer::class)
    @SerialName("download_extensions")
    var downloadExtensions: @RawValue RealmList<String> = RealmList(),
    @SerialName("when_onlyoffice_opening")
    var whenOnlyoffice: Boolean = false,
    @SerialName("onlyoffice_extension")
    var onlyofficeExtension: String? = null,
) : RealmObject(), Parcelable
