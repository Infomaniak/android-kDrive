/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import com.google.gson.annotations.SerializedName

enum class FileActivityType {
    @SerializedName("file_access")
    FILE_ACCESS,
    @SerializedName("file_create")
    FILE_CREATE,
    @SerializedName("file_rename")
    FILE_RENAME,
    @SerializedName("file_rename_alias")
    FILE_RENAME_ALIAS,
    @SerializedName("file_move")
    FILE_MOVE_IN,
    @SerializedName("file_move_out")
    FILE_MOVE_OUT,
    @SerializedName("file_trash")
    FILE_TRASH,
    @SerializedName("file_restore")
    FILE_RESTORE,
    @SerializedName("file_delete")
    FILE_DELETE,
    @SerializedName("file_update")
    FILE_UPDATE,
    @SerializedName("file_favorite_create")
    FILE_FAVORITE_CREATE,
    @SerializedName("file_favorite_remove")
    FILE_FAVORITE_REMOVE,
    @SerializedName("file_share_create")
    FILE_SHARE_CREATE,
    @SerializedName("file_share_update")
    FILE_SHARE_UPDATE,
    @SerializedName("file_share_delete")
    FILE_SHARE_DELETE,
    @SerializedName("file_categorize")
    FILE_CATEGORIZE,
    @SerializedName("file_uncategorize")
    FILE_UNCATEGORIZE,
    @SerializedName("file_color_update")
    FILE_COLOR_UPDATE,
    @SerializedName("file_color_delete")
    FILE_COLOR_DELETE,
    @SerializedName("share_link_create")
    SHARE_LINK_CREATE,
    @SerializedName("share_link_update")
    SHARE_LINK_UPDATE,
    @SerializedName("share_link_delete")
    SHARE_LINK_DELETE,
    @SerializedName("share_link_show")
    SHARE_LINK_SHOW,
    @SerializedName("comment_create")
    COMMENT_CREATE,
    @SerializedName("comment_update")
    COMMENT_UPDATE,
    @SerializedName("comment_delete")
    COMMENT_DELETE,
    @SerializedName("comment_like")
    COMMENT_LIKE,
    @SerializedName("comment_unlike")
    COMMENT_UNLIKE,
    @SerializedName("comment_resolve")
    COMMENT_RESOLVE,
    @SerializedName("collaborative_folder_access")
    COLLABORATIVE_FOLDER_ACCESS,
    @SerializedName("collaborative_folder_create")
    COLLABORATIVE_FOLDER_CREATE,
    @SerializedName("collaborative_folder_update")
    COLLABORATIVE_FOLDER_UPDATE,
    @SerializedName("collaborative_folder_delete")
    COLLABORATIVE_FOLDER_DELETE,
    @SerializedName("collaborative_user_access")
    COLLABORATIVE_USER_ACCESS,
    @SerializedName("collaborative_user_create")
    COLLABORATIVE_USER_CREATE,
    @SerializedName("collaborative_user_delete")
    COLLABORATIVE_USER_DELETE,
    UNKNOWN,
}
