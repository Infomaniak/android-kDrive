/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.models.drive.DrivePackFunctionality
import com.infomaniak.drive.data.models.drive.DrivePreferences
import com.infomaniak.drive.data.models.drive.DriveUsersCategories
import io.realm.annotations.RealmModule

object RealmModules {

    @RealmModule(classes = [File::class, Rights::class, FileActivity::class])
    class LocalFilesModule

    @RealmModule(classes = [UploadFile::class, SyncSettings::class, MediaFolder::class])
    class SyncFilesModule

    @RealmModule(classes = [AppSettings::class])
    class AppSettingsModule

    @RealmModule(classes = [Drive::class, DrivePackFunctionality::class, DrivePreferences::class, DriveUsersCategories::class, DriveUser::class, Team::class, TeamDetails::class])
    class DriveFilesModule

}