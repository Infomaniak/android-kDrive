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
package com.infomaniak.drive.data.api

import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.ApiErrorCode

object ErrorCode {

    const val CATEGORY_ALREADY_EXISTS = "category_already_exist_error"
    const val COLLABORATIVE_FOLDER_ALREADY_EXISTS_FOR_FILE = "collaborative_folder_already_exists_for_file"
    const val CONFLICT_ERROR = "conflict_error"
    const val DESTINATION_ALREADY_EXISTS = "destination_already_exists"
    const val EXTERNAL_IMPORT_IN_PROGRESS_ERROR = "external_import_in_progress_in_directory_error"
    const val LIMIT_EXCEEDED_ERROR = "limit_exceeded_error"
    const val LOCK_ERROR = "lock_error"
    const val NO_DRIVE = "no_drive"
    const val SHARE_LINK_ALREADY_EXISTS = "file_share_link_already_exists"
    const val PASSWORD_NOT_VALID = "password_not_valid"
    const val PUBLIC_SHARE_LINK_IS_NOT_VALID = "link_is_not_valid"
    const val QUOTA_EXCEEDED_ERROR = "quota_exceeded_error"

    val apiErrorCodes = listOf(
        ApiErrorCode(CATEGORY_ALREADY_EXISTS, R.string.errorCategoryAlreadyExists),
        ApiErrorCode(COLLABORATIVE_FOLDER_ALREADY_EXISTS_FOR_FILE, R.string.anErrorHasOccurred),
        ApiErrorCode(CONFLICT_ERROR, R.string.errorConflict),
        ApiErrorCode(DESTINATION_ALREADY_EXISTS, R.string.errorFileAlreadyExists),
        ApiErrorCode(EXTERNAL_IMPORT_IN_PROGRESS_ERROR, R.string.errorExternalImportInProgress),
        ApiErrorCode(LOCK_ERROR, R.string.errorFileLocked),
        ApiErrorCode(LIMIT_EXCEEDED_ERROR, R.string.errorLimitExceeded),
        ApiErrorCode(NO_DRIVE, R.string.noDriveTitle),
        ApiErrorCode(SHARE_LINK_ALREADY_EXISTS, R.string.errorShareLink),
        ApiErrorCode(QUOTA_EXCEEDED_ERROR, R.string.errorQuotaExceeded),
    )
}
