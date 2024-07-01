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
package com.infomaniak.drive.data.models

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.FORMAT_DATE_HOUR_MINUTE
import com.infomaniak.lib.core.utils.FORMAT_FULL_DATE
import com.infomaniak.lib.core.utils.format
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import java.util.Date
import java.util.concurrent.TimeUnit

open class FileActivity(
    @PrimaryKey var id: Int = 0,
    var action: String = "",
    @SerializedName("created_at")
    var createdAt: Date = Date(),
    var file: File? = null,
    @SerializedName("file_id")
    var fileId: Int = 0,
    @SerializedName("new_path")
    var newPath: String = "",
    @SerializedName("old_path")
    var oldPath: String = "",

    /**
     * Local
     */
    var userId: Int? = null
) : RealmObject() {

    @Ignore
    var user: DriveUser? = null

    @Ignore
    var mergedFileActivities: ArrayList<FileActivity> = arrayListOf()

    val homeTranslation: Int
        get() {
            return when (getAction()) {
                FileActivityType.FILE_CREATE -> {
                    if (file?.isFolder() == true) R.plurals.fileActivityFolderCreate else R.plurals.fileActivityFileCreate
                }
                FileActivityType.FILE_TRASH -> {
                    if (file?.isFolder() == true) R.plurals.fileActivityFolderTrash else R.plurals.fileActivityFileTrash
                }
                FileActivityType.FILE_RESTORE -> {
                    if (file?.isFolder() == true) R.plurals.fileActivityFolderRestore else R.plurals.fileActivityFileRestore
                }
                FileActivityType.FILE_UPDATE -> R.plurals.fileActivityFileUpdate
                FileActivityType.COMMENT_CREATE -> R.plurals.fileActivityCommentCreate
                else -> R.plurals.fileActivityUnknown
            }
        }

    fun translation(isFolder: Boolean): Int = when (getAction()) {
        FileActivityType.FILE_ACCESS -> {
            if (isFolder) R.string.fileDetailsActivityFolderAccess else R.string.fileDetailsActivityFileAccess
        }
        FileActivityType.FILE_CREATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderCreate else R.string.fileDetailsActivityFileCreate
        }
        FileActivityType.FILE_RENAME -> {
            if (isFolder) R.string.fileDetailsActivityFolderRename else R.string.fileDetailsActivityFileRename
        }
        FileActivityType.FILE_MOVE_IN,
        FileActivityType.FILE_MOVE_OUT -> {
            if (isFolder) R.string.fileDetailsActivityFolderMove else R.string.fileDetailsActivityFileMove
        }
        FileActivityType.FILE_TRASH -> {
            if (isFolder) R.string.fileDetailsActivityFolderTrash else R.string.fileDetailsActivityFileTrash
        }
        FileActivityType.FILE_RESTORE -> {
            if (isFolder) R.string.fileDetailsActivityFolderRestore else R.string.fileDetailsActivityFileRestore
        }
        FileActivityType.FILE_DELETE -> {
            if (isFolder) R.string.fileDetailsActivityFolderDelete else R.string.fileDetailsActivityFileDelete
        }
        FileActivityType.FILE_UPDATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderUpdate else R.string.fileDetailsActivityFileUpdate
        }
        FileActivityType.FILE_FAVORITE_CREATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderFavoriteCreate else R.string.fileDetailsActivityFileFavoriteCreate
        }
        FileActivityType.FILE_FAVORITE_REMOVE -> {
            if (isFolder) R.string.fileDetailsActivityFolderFavoriteRemove else R.string.fileDetailsActivityFileFavoriteRemove
        }
        FileActivityType.FILE_SHARE_CREATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareCreate else R.string.fileDetailsActivityFileShareCreate
        }
        FileActivityType.FILE_SHARE_UPDATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareUpdate else R.string.fileDetailsActivityFileShareUpdate
        }
        FileActivityType.FILE_SHARE_DELETE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareDelete else R.string.fileDetailsActivityFileShareDelete
        }
        FileActivityType.SHARE_LINK_CREATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareLinkCreate else R.string.fileDetailsActivityFileShareLinkCreate
        }
        FileActivityType.SHARE_LINK_UPDATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareLinkUpdate else R.string.fileDetailsActivityFileShareLinkUpdate
        }
        FileActivityType.SHARE_LINK_DELETE -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareLinkDelete else R.string.fileDetailsActivityFileShareLinkDelete
        }
        FileActivityType.SHARE_LINK_SHOW -> {
            if (isFolder) R.string.fileDetailsActivityFolderShareLinkShow else R.string.fileDetailsActivityFileShareLinkShow
        }
        FileActivityType.COMMENT_CREATE -> {
            if (isFolder) R.string.fileDetailsActivityFolderCommentCreate else R.string.fileDetailsActivityFileCommentCreate
        }
        FileActivityType.COMMENT_UPDATE -> R.string.fileDetailsActivityFileCommentUpdate
        FileActivityType.COMMENT_DELETE -> R.string.fileDetailsActivityFileCommentDelete
        FileActivityType.COMMENT_LIKE -> R.string.fileDetailsActivityFileCommentLike
        FileActivityType.COMMENT_UNLIKE -> R.string.fileDetailsActivityFileCommentUnlike
        FileActivityType.COMMENT_RESOLVE -> R.string.fileDetailsActivityFileCommentUpdate
        FileActivityType.COLLABORATIVE_FOLDER_CREATE -> R.string.fileActivityCollaborativeFolderCreate
        FileActivityType.COLLABORATIVE_FOLDER_UPDATE -> R.string.fileActivityCollaborativeFolderUpdate
        FileActivityType.COLLABORATIVE_FOLDER_DELETE -> R.string.fileActivityCollaborativeFolderDelete
        FileActivityType.FILE_CATEGORIZE -> R.string.fileDetailsActivityFileCategorize
        FileActivityType.FILE_UNCATEGORIZE -> R.string.fileDetailsActivityFileUncategorize
        FileActivityType.FILE_COLOR_UPDATE -> R.string.fileDetailsActivityFileColorUpdate
        FileActivityType.FILE_COLOR_DELETE -> R.string.fileDetailsActivityFileColorDelete
    }

    fun getDay(context: Context): String {
        val dateDiff = Date().time / 1000 - createdAt.time / 1000

        return when (TimeUnit.SECONDS.toDays(dateDiff).toInt()) {
            0 -> context.getString(R.string.allToday)
            1 -> context.getString(R.string.allYesterday)
            else -> createdAt.format(FORMAT_FULL_DATE)
        }
    }

    fun getHour(): String {
        return createdAt.format(FORMAT_DATE_HOUR_MINUTE)
    }

    fun getAction(): FileActivityType {
        return if (action == "file_move") FileActivityType.FILE_MOVE_IN
        else FileActivityType.valueOf(action.uppercase())
    }
}
