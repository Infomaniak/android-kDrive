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
import com.infomaniak.drive.data.models.file.sharelink.ShareLinkCapabilities
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.RealmClass
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
@RealmClass(embedded = true)
open class ShareLink(
    var url: String = "",
    var _right: String = ShareLinkFilePermission.RESTRICTED.name,
    @SerializedName("valid_until") var validUntil: Date? = null,
    var capabilities: ShareLinkCapabilities? = null,
) : RealmObject(), Parcelable {

    var right
        get() = ShareLinkFilePermission.valueOf(_right)
        set(value) {
            _right = value.name
        }

    /**
     * Local properties
     */
    @Ignore
    var newPassword: String? = null

    @Ignore
    var newRight = ShareLinkFilePermission.RESTRICTED

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

    data class ShareLinkSettings(
        @SerializedName("can_comment") var canComment: Boolean? = null,
        @SerializedName("can_download") var canDownload: Boolean? = null,
        @SerializedName("can_edit") var canEdit: Boolean? = null,
        @SerializedName("can_see_info") var canSeeInfo: Boolean? = null,
        @SerializedName("can_see_stats") var canSeeStats: Boolean? = null,
        var password: String? = null,
        var right: ShareLinkFilePermission? = null,
        @SerializedName("valid_until") var validUntil: Date? = null,
    )
}
