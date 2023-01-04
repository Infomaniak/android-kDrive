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
import io.realm.RealmObject
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize

@Parcelize
@RealmClass(embedded = true)
open class FileExternalImport(
    var id: Int = 0,
    var application: String = "",
    @SerializedName("account_name")
    var accountName: String = "",
    var status: String = "",
    var path: String = "",
    @SerializedName("directory_id")
    var directoryId: Int = 0,
    @SerializedName("has_shared_files")
    var hasSharedFiles: String = "",
    @SerializedName("created_at")
    var createdAt: Long = 0,
    @SerializedName("updated_at")
    var updatedAt: Long = 0,
    @SerializedName("count_success_files")
    var countSuccessFiles: Int = 0,
    @SerializedName("count_failed_files")
    var countFailedFiles: Int = 0,
) : RealmObject(), Parcelable {

    enum class FileExternalImportStatus(val value: String) {
        WAITING("waiting"),
        IN_PROGRESS("in_progress"),
        DONE("done"),
        FAILED("failed"),
        CANCELING("canceling"),
        CANCELED("canceled"),
    }
}
