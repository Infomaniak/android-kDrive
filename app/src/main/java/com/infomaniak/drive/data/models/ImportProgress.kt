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
package com.infomaniak.drive.data.models

import com.google.gson.annotations.SerializedName

data class ImportProgress(
    @SerializedName("total_files")
    val totalFiles: Int = 0,
    @SerializedName("total_files_processed")
    val totalFilesProcessed: Int = 0,
    @SerializedName("total_successes")
    val totalSuccesses: Int = 0,
    @SerializedName("total_errors")
    val totalErrors: Int = 0,
) {
    val isDeterminate: Boolean get() = totalFiles > 0

    val percent: Int get() = if (isDeterminate) (totalFilesProcessed * 100 / totalFiles).coerceIn(0, 100) else 0
}
