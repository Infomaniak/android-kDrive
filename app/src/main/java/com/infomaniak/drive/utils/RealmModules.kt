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
package com.infomaniak.drive.utils

import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.drive.*
import com.infomaniak.drive.data.models.file.FileConversion
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.models.file.FileVersion
import com.infomaniak.drive.data.models.file.dropbox.DropBoxCapabilities
import com.infomaniak.drive.data.models.file.dropbox.DropBoxSize
import com.infomaniak.drive.data.models.file.dropbox.DropBoxValidity
import com.infomaniak.drive.data.models.file.sharelink.ShareLinkCapabilities
import io.realm.annotations.RealmModule

object RealmModules {

    @RealmModule(
        classes = [
            File::class, Rights::class, FileActivity::class, FileCategory::class,
            ShareLink::class, DropBox::class, FileVersion::class, FileConversion::class, FileExternalImport::class,
            DropBoxCapabilities::class, ShareLinkCapabilities::class, DropBoxValidity::class, DropBoxSize::class
        ]
    )
    class LocalFilesModule

    @RealmModule(classes = [UploadFile::class, SyncSettings::class, MediaFolder::class])
    class SyncFilesModule

    @RealmModule(classes = [AppSettings::class])
    class AppSettingsModule

    @RealmModule(
        classes = [
            Drive::class, DrivePreferences::class, DriveUsersCategories::class, DriveUser::class,
            Team::class, TeamDetails::class, DriveTeamsCategories::class, Category::class, CategoryRights::class,
            DriveCapabilities::class, DrivePack::class, DrivePackCapabilities::class, DriveRights::class, DriveAccount::class,
        ]
    )
    class DriveFilesModule

}
