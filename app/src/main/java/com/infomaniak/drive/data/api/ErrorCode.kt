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
package com.infomaniak.drive.data.api

import com.infomaniak.core.network.utils.ApiErrorCode
import com.infomaniak.drive.R

object ErrorCode {

    const val CATEGORY_ALREADY_EXISTS = "category_already_exist_error"
    const val COLLABORATIVE_FOLDER_ALREADY_EXISTS_FOR_FILE = "collaborative_folder_already_exists_for_file"
    const val CONFLICT_ERROR = "conflict_error"
    const val DESTINATION_ALREADY_EXISTS = "destination_already_exists"
    const val DRIVE_MAINTENANCE = "drive_is_in_maintenance_error"
    const val EXTERNAL_IMPORT_IN_PROGRESS_ERROR = "external_import_in_progress_in_directory_error"
    const val FORBIDDEN_ERROR = "forbidden_error"
    const val LIMIT_EXCEEDED_ERROR = "limit_exceeded_error"
    const val LOCK_ERROR = "lock_error"
    const val NETWORK_ERROR = "networkError"
    const val NO_DRIVE = "no_drive"
    const val OBJECT_NOT_FOUND = "object_not_found"
    const val PASSWORD_NOT_VALID = "password_not_valid"
    const val PRODUCT_BLOCKED = "product_blocked"
    const val PRODUCT_MAINTENANCE = "product_maintenance"
    const val PUBLIC_SHARE_LINK_IS_NOT_VALID = "link_is_not_valid"
    const val QUOTA_EXCEEDED_ERROR = "quota_exceeded_error"
    const val SHARE_LINK_ALREADY_EXISTS = "file_share_link_already_exists"
    const val STILL_UPLOADING_ERROR = "still_uploading_error"
    const val YOU_MUST_ADD_AT_LEAST_ONE_FILE = "you_must_add_at_least_one_file"

    val apiErrorCodes = listOf(
        ApiErrorCode(CATEGORY_ALREADY_EXISTS, R.string.errorCategoryAlreadyExists),
        ApiErrorCode(COLLABORATIVE_FOLDER_ALREADY_EXISTS_FOR_FILE, R.string.anErrorHasOccurred),
        ApiErrorCode(CONFLICT_ERROR, R.string.errorConflict),
        ApiErrorCode(DESTINATION_ALREADY_EXISTS, R.string.errorFileAlreadyExists),
        ApiErrorCode(DRIVE_MAINTENANCE, R.string.driveMaintenanceDescription),
        ApiErrorCode(EXTERNAL_IMPORT_IN_PROGRESS_ERROR, R.string.errorExternalImportInProgress),
        ApiErrorCode(FORBIDDEN_ERROR, R.string.accessDeniedTitle),
        ApiErrorCode(LIMIT_EXCEEDED_ERROR, R.string.errorLimitExceeded),
        ApiErrorCode(LOCK_ERROR, R.string.errorFileLocked),
        ApiErrorCode(NETWORK_ERROR, R.string.errorNetwork),
        ApiErrorCode(NO_DRIVE, R.string.noDriveTitle),
        ApiErrorCode(OBJECT_NOT_FOUND, R.string.uploadFolderNotFoundError),
        ApiErrorCode(PRODUCT_BLOCKED, R.string.allDriveBlockedDescription),
        ApiErrorCode(PRODUCT_MAINTENANCE, R.string.driveMaintenanceDescription),
        ApiErrorCode(QUOTA_EXCEEDED_ERROR, R.string.errorQuotaExceeded),
        ApiErrorCode(SHARE_LINK_ALREADY_EXISTS, R.string.errorShareLink),
        ApiErrorCode(STILL_UPLOADING_ERROR, R.string.errorStillUploading),
        ApiErrorCode(YOU_MUST_ADD_AT_LEAST_ONE_FILE, R.string.errorDownloadPermission),
    )
}
