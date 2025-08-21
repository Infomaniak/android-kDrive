/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.file.sharelink.ShareLinkCapabilities
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.api.ApiController.gson
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.RealmClass
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@RealmClass(embedded = true)
open class ShareLink(
    var url: String = "",
    @SerializedName("file_id")
    var fileId: Int? = null,
    @SerializedName("right")
    var _right: String = ShareLinkFilePermission.RESTRICTED.name,
    @SerializedName("valid_until")
    var validUntil: Date? = null,
    var capabilities: ShareLinkCapabilities? = null,
    @SerializedName("access_blocked")
    var accessBlocked: Boolean = false,
) : RealmObject(), Parcelable {

    inline var right
        get() = enumValueOfOrNull<ShareLinkFilePermission>(_right)
        set(value) {
            value?.let { _right = value.name }
        }

    /**
     * Local properties
     */
    @Ignore
    var newPassword: String? = null

    @Ignore
    var newRight: ShareLinkFilePermission? = null

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
            R.string.shareLinkPrivateRightTitle,
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
            R.string.shareLinkPrivateRightTitle,
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
            R.string.shareLinkPrivateRightTitle,
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

    inner class ShareLinkSettings(
        @SerializedName("can_download")
        var canDownload: Boolean? = capabilities?.canDownload,
        @SerializedName("can_edit")
        var canEdit: Boolean? = capabilities?.canEdit,
        @SerializedName("can_see_stats")
        var canSeeStats: Boolean? = capabilities?.canSeeStats,
        var password: String? = newPassword,
        var right: ShareLinkFilePermission? = newRight,
        @SerializedName("valid_until")
        var validUntil: Date? = this@ShareLink.validUntil,
    ) {
        fun toJsonElement(): JsonObject {
            return with(gson.newBuilder().serializeNulls().create()) {
                JsonParser.parseString(toJson(this@ShareLinkSettings)).asJsonObject.apply {
                    if (password == null) remove(ShareLinkSettings::password.name)
                    if (right == null) remove(ShareLinkSettings::right.name)
                    if (AccountUtils.getCurrentDrive()?.isKSuiteFreeTier == true) remove("valid_until")
                }
            }
        }
    }
}
