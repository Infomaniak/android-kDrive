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

import com.google.gson.annotations.SerializedName

data class MqttNotification(
    val uid: String,
    val action: MqttAction? = null,
    @SerializedName("drive_id")
    val driveId: Int,

    // Only for File Action notification
    @SerializedName("file_id")
    val fileId: Int? = null,
    @SerializedName("parent_id")
    val parentId: Int? = null,
    @SerializedName("simple_action")
    val simpleAction: String? = null,

    // Only for external import notification
    @SerializedName("user_id")
    val userId: Int? = null,
    @SerializedName("import_id")
    val importId: Int? = null,

    // Only for action progress notification
    @SerializedName("action_uuid")
    val actionUuid: String? = null,
    val progress: ActionProgress? = null,
) {
    fun isExternalImportNotification() = importId != null

    fun isFileActionNotification() = fileId != null

    fun isProgressNotification() = progress != null

    fun isImportTerminated(): Boolean {
        return action == MqttAction.EXTERNAL_IMPORT_FINISHED ||
                action == MqttAction.EXTERNAL_IMPORT_CANCELED ||
                action == MqttAction.EXTERNAL_IMPORT_ERROR
    }
}
