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

import com.infomaniak.core.utils.ApiEnum

enum class FileActivityType(override val apiValue: String) : ApiEnum {
    FILE_ACCESS(apiValue = "file_access"),
    FILE_CREATE(apiValue = "file_create"),
    FILE_RENAME(apiValue = "file_rename"),
    FILE_RENAME_ALIAS(apiValue = "file_rename_alias"),
    FILE_MOVE_IN(apiValue = "file_move"),
    FILE_MOVE_OUT(apiValue = "file_move_out"),
    FILE_TRASH(apiValue = "file_trash"),
    FILE_RESTORE(apiValue = "file_restore"),
    FILE_DELETE(apiValue = "file_delete"),
    FILE_UPDATE(apiValue = "file_update"),
    FILE_FAVORITE_CREATE(apiValue = "file_favorite_create"),
    FILE_FAVORITE_REMOVE(apiValue = "file_favorite_remove"),
    FILE_SHARE_CREATE(apiValue = "file_share_create"),
    FILE_SHARE_UPDATE(apiValue = "file_share_update"),
    FILE_SHARE_DELETE(apiValue = "file_share_delete"),
    FILE_CATEGORIZE(apiValue = "file_categorize"),
    FILE_UNCATEGORIZE(apiValue = "file_uncategorize"),
    FILE_COLOR_UPDATE(apiValue = "file_color_update"),
    FILE_COLOR_DELETE(apiValue = "file_color_delete"),
    SHARE_LINK_CREATE(apiValue = "share_link_create"),
    SHARE_LINK_UPDATE(apiValue = "share_link_update"),
    SHARE_LINK_DELETE(apiValue = "share_link_delete"),
    SHARE_LINK_SHOW(apiValue = "share_link_show"),
    COMMENT_CREATE(apiValue = "comment_create"),
    COMMENT_UPDATE(apiValue = "comment_update"),
    COMMENT_DELETE(apiValue = "comment_delete"),
    COMMENT_LIKE(apiValue = "comment_like"),
    COMMENT_UNLIKE(apiValue = "comment_unlike"),
    COMMENT_RESOLVE(apiValue = "comment_resolve"),
    COLLABORATIVE_FOLDER_ACCESS(apiValue = "collaborative_folder_access"),
    COLLABORATIVE_FOLDER_CREATE(apiValue = "collaborative_folder_create"),
    COLLABORATIVE_FOLDER_UPDATE(apiValue = "collaborative_folder_update"),
    COLLABORATIVE_FOLDER_DELETE(apiValue = "collaborative_folder_delete"),
    COLLABORATIVE_USER_ACCESS(apiValue = "collaborative_user_access"),
    COLLABORATIVE_USER_CREATE(apiValue = "collaborative_user_create"),
    COLLABORATIVE_USER_DELETE(apiValue = "collaborative_user_delete"),
}
