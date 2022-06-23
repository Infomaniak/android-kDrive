/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue

@Parcelize
@RealmClass(embedded = true)
open class FileConversion(
    @SerializedName("when_download")
    var whenDownload: Boolean = false,
    @SerializedName("download_extensions")
    var downloadExtensions: @RawValue RealmList<String> = RealmList(),
    @SerializedName("when_onlyoffice")
    var whenOnlyoffice: Boolean = false,
    @SerializedName("onlyoffice_extension")
    var onlyofficeExtension: String? = null,
) : RealmObject(), Parcelable
