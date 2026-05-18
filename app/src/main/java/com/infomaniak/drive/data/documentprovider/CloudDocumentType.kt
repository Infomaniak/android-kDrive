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
package com.infomaniak.drive.data.documentprovider

import com.infomaniak.drive.data.models.UserDrive

sealed interface CloudDocumentType {
    data object RootFolder : CloudDocumentType
    data object SharedWithMeFolder : CloudDocumentType
    data object MySharesFolder : CloudDocumentType
    data object DriveFolder : CloudDocumentType
    data object DriveFromMySharesFolder : CloudDocumentType
    data class FileOrFolder(val fileId: Int, val userDrive: UserDrive) : CloudDocumentType
}
