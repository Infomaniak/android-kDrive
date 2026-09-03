/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalSerializationApi::class)

package com.infomaniak.drive.backup.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SyncSettingsBackupModel(
    @ProtoNumber(1)
    val userId: Int,
    @ProtoNumber(2)
    val createDatedSubFolders: Boolean,
    @ProtoNumber(3)
    val driveId: Int,
    @ProtoNumber(4)
    val lastSync: Long,
    @ProtoNumber(5)
    val syncFolder: Int,
    @ProtoNumber(6)
    val syncImmediately: Boolean,
    @ProtoNumber(7)
    val syncInterval: Long,
    @ProtoNumber(8)
    val syncVideo: Boolean,
    @ProtoNumber(9)
    val deleteAfterSync: Boolean,
    @ProtoNumber(10)
    val onlyWifiSyncMedia: Boolean,
)
