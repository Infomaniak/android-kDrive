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

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.R
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class ShareLink(
    val url: String = "",
    val name: String = "",
    val path: String = "",
    val type: String = "",
    var password: String? = null,
    val onlyoffice: Boolean = false,
    var permission: ShareLinkPermission = ShareLinkPermission.PUBLIC,
    @SerializedName("file_id") val fileId: Int = 0,
    @SerializedName("created_by") val createdBy: Int = 0,
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0,
    @SerializedName("mime_type") val mimeType: String = "",
    @SerializedName("valid_until") var validUntil: Date? = null,
    @SerializedName("can_edit") val canEdit: Boolean = false,
    @SerializedName("show_stats") val showStats: Boolean = false,
    @SerializedName("converted_type") val convertedType: String = "",
    @SerializedName("block_comments") var blockComments: Boolean = true,
    @SerializedName("block_downloads") var blockDownloads: Boolean = false,
    @SerializedName("block_information") var blockInformation: Boolean = true,
    @SerializedName("onlyoffice_convert_extension") val onlyofficeConvertExtension: Boolean = false
) : Parcelable {

    @Parcelize
    enum class ShareLinkPermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int,
        val apiValue: String
    ) : Permission {

        @SerializedName("public")
        PUBLIC(
            R.drawable.ic_view,
            R.string.shareLinkPublicRightTitle,
            R.string.shareLinkPublicRightDescription,
            "public"
        ),

        @SerializedName("inherit")
        INHERIT(
            R.drawable.ic_users,
            R.string.shareLinkDriveUsersRightTitle,
            R.string.shareLinkDriveUsersRightDescription,
            "inherit"
        ),

        @SerializedName("password")
        PASSWORD(
            R.drawable.ic_lock,
            R.string.shareLinkPasswordRightTitle,
            R.string.shareLinkPasswordRightDescription,
            "password"
        )
    }

    @Parcelize
    enum class OfficePermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int,
        val apiValue: Boolean
    ) : Permission {
        READ(
            R.drawable.ic_view,
            R.string.shareLinkOfficePermissionReadTitle,
            R.string.shareLinkOfficePermissionReadDescription,
            false
        ),
        WRITE(
            R.drawable.ic_edit,
            R.string.shareLinkOfficePermissionWriteTitle,
            R.string.shareLinkOfficePermissionWriteDescription,
            true
        )
    }
}
