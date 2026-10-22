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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MqttAction {
    @SerialName("file_move")
    @SerializedName("file_move")
    FILE_MOVE,

    @SerialName("file_update")
    @SerializedName("file_update")
    FILE_UPDATE,

    @SerialName("file_rename")
    @SerializedName("file_rename")
    FILE_RENAME,

    @SerialName("file_restore")
    @SerializedName("file_restore")
    FILE_RESTORE,

    @SerialName("file_trash")
    @SerializedName("file_trash")
    FILE_TRASH,

    @SerialName("file_trash_inherited")
    @SerializedName("file_trash_inherited")
    FILE_TRASH_INHERITED,

    @SerialName("file_delete")
    @SerializedName("file_delete")
    FILE_DELETE,

    @SerialName("file_create")
    @SerializedName("file_create")
    FILE_CREATE,

    @SerialName("file_update_mime_type")
    @SerializedName("file_update_mime_type")
    FILE_UPDATE_MIME_TYPE,

    @SerialName("bulk_file_move")
    @SerializedName("bulk_file_move")
    BULK_FILE_MOVE,

    @SerialName("bulk_file_trash")
    @SerializedName("bulk_file_trash")
    BULK_FILE_TRASH,

    @SerialName("bulk_file_copy")
    @SerializedName("bulk_file_copy")
    BULK_FILE_COPY,

    @SerialName("import_started")
    @SerializedName("import_started")
    EXTERNAL_IMPORT_STARTED,

    @SerialName("import_file_created")
    @SerializedName("import_file_created")
    EXTERNAL_IMPORT_FILE_CREATED,

    @SerialName("import_canceling")
    @SerializedName("import_canceling")
    EXTERNAL_IMPORT_CANCELING,

    @SerialName("import_canceled")
    @SerializedName("import_canceled")
    EXTERNAL_IMPORT_CANCELED,

    @SerialName("import_errored")
    @SerializedName("import_errored")
    EXTERNAL_IMPORT_ERROR,

    @SerialName("import_finished")
    @SerializedName("import_finished")
    EXTERNAL_IMPORT_FINISHED,
}
