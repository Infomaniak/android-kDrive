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
    var permission: ShareLinkFilePermission = ShareLinkFilePermission.RESTRICTED,
    @SerializedName("file_id") val fileId: Int = 0,
    @SerializedName("created_by") val createdBy: Int = 0,
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0,
    @SerializedName("mime_type") val mimeType: String = "",
    @SerializedName("valid_until") var validUntil: Date? = null,
    @SerializedName("can_edit") var canEdit: Boolean = false,
    @SerializedName("show_stats") val showStats: Boolean = false,
    @SerializedName("converted_type") val convertedType: String = "",
    @SerializedName("block_comments") var blockComments: Boolean = true,
    @SerializedName("block_downloads") var blockDownloads: Boolean = false,
    @SerializedName("block_information") var blockInformation: Boolean = true,
    @SerializedName("onlyoffice_convert_extension") val onlyofficeConvertExtension: Boolean = false
) : Parcelable {

    @Parcelize
    enum class ShareLinkFilePermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int
    ) : Permission {

        @SerializedName("public")
        PUBLIC(
            R.drawable.ic_unlock,
            R.string.shareLinkPublicRightTitle,
            R.string.shareLinkPublicRightFileDescriptionShort
        ),

        @SerializedName("inherit")
        RESTRICTED(
            R.drawable.ic_lock,
            R.string.shareLinkRestrictedRightTitle,
            R.string.shareLinkRestrictedRightFileDescriptionShort
        ),

        @SerializedName("password")
        PASSWORD(-1, -1, -1)
    }

    @Parcelize
    enum class ShareLinkFolderPermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int
    ) : Permission {

        @SerializedName("public")
        PUBLIC(
            R.drawable.ic_unlock,
            R.string.shareLinkPublicRightTitle,
            R.string.shareLinkPublicRightFolderDescriptionShort
        ),

        @SerializedName("inherit")
        RESTRICTED(
            R.drawable.ic_lock,
            R.string.shareLinkRestrictedRightTitle,
            R.string.shareLinkRestrictedRightFolderDescriptionShort
        ),
    }

    @Parcelize
    enum class ShareLinkDocumentPermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int
    ) : Permission {

        @SerializedName("public")
        PUBLIC(
            R.drawable.ic_unlock,
            R.string.shareLinkPublicRightTitle,
            R.string.shareLinkPublicRightDocumentDescriptionShort
        ),

        @SerializedName("inherit")
        RESTRICTED(
            R.drawable.ic_lock,
            R.string.shareLinkRestrictedRightTitle,
            R.string.shareLinkRestrictedRightDocumentDescriptionShort
        ),
    }

    interface EditPermission : Permission {
        val apiValue: Boolean
    }

    @Parcelize
    enum class OfficeFilePermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int,
        override val apiValue: Boolean
    ) : EditPermission {
        READ(
            R.drawable.ic_view,
            R.string.shareLinkOfficePermissionReadTitle,
            R.string.shareLinkOfficePermissionReadFileDescription,
            false
        ),
        WRITE(
            R.drawable.ic_edit,
            R.string.shareLinkOfficePermissionWriteTitle,
            R.string.shareLinkOfficePermissionWriteFileDescription,
            true
        )
    }

    @Parcelize
    enum class OfficeFolderPermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int,
        override val apiValue: Boolean
    ) : EditPermission {
        READ(
            R.drawable.ic_view,
            R.string.shareLinkOfficePermissionReadTitle,
            R.string.shareLinkOfficePermissionReadFolderDescription,
            false
        ),
        WRITE(
            R.drawable.ic_edit,
            R.string.shareLinkOfficePermissionWriteTitle,
            R.string.shareLinkOfficePermissionWriteFolderDescription,
            true
        )
    }
}
