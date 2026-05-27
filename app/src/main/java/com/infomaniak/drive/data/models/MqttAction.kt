/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

enum class MqttAction {
    @SerializedName("file_move")
    FILE_MOVE,

    @SerializedName("file_update")
    FILE_UPDATE,

    @SerializedName("file_rename")
    FILE_RENAME,

    @SerializedName("file_restore")
    FILE_RESTORE,

    @SerializedName("file_trash")
    FILE_TRASH,

    @SerializedName("file_trash_inherited")
    FILE_TRASH_INHERITED,

    @SerializedName("file_delete")
    FILE_DELETE,

    @SerializedName("file_create")
    FILE_CREATE,

    @SerializedName("file_update_mime_type")
    FILE_UPDATE_MIME_TYPE,

    @SerializedName("bulk_file_move")
    BULK_FILE_MOVE,

    @SerializedName("bulk_file_trash")
    BULK_FILE_TRASH,

    @SerializedName("bulk_file_copy")
    BULK_FILE_COPY,

    @SerializedName("import_started")
    EXTERNAL_IMPORT_STARTED,

    @SerializedName("import_file_created")
    EXTERNAL_IMPORT_FILE_CREATED,

    @SerializedName("import_canceling")
    EXTERNAL_IMPORT_CANCELING,

    @SerializedName("import_canceled")
    EXTERNAL_IMPORT_CANCELED,

    @SerializedName("import_errored")
    EXTERNAL_IMPORT_ERROR,

    @SerializedName("import_finished")
    EXTERNAL_IMPORT_FINISHED,
}
