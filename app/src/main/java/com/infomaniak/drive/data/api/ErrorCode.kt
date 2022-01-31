/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import androidx.annotation.StringRes
import com.infomaniak.drive.R
import com.infomaniak.lib.core.models.ApiResponse

@Suppress("unused")
enum class ErrorCode(val code: String, @StringRes val translateRes: Int) {
    AN_ERROR_HAS_OCCURRED("an_error_has_occured", R.string.anErrorHasOccurred),
    COLLABORATIVE_FOLDER_ALREADY_EXISTS_FOR_FILE("collaborative_folder_already_exists_for_file", R.string.anErrorHasOccurred),
    CONFLICT_ERROR("conflict_error", R.string.errorConflict),
    DESTINATION_ALREADY_EXISTS("destination_already_exists", R.string.errorFileCreate),
    SHARE_LINK_ALREADY_EXISTS("file_share_link_already_exists", R.string.errorShareLink),
    CATEGORY_ALREADY_EXISTS("category_already_exist_error", R.string.errorCategoryAlreadyExists);

    companion object {

        @StringRes
        fun <T> ApiResponse<T>.translateError(): Int {
            return formatError().translateRes
        }

        fun <T> ApiResponse<T>.formatError(): ErrorCode {
            return if (error?.code == null) AN_ERROR_HAS_OCCURRED
            else values().firstOrNull { it.code.equals(error?.code, true) }
                ?: AN_ERROR_HAS_OCCURRED
        }
    }
}