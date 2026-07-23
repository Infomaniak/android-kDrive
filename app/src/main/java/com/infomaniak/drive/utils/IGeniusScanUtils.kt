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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File

interface IGeniusScanUtils<InputType, OutputType> {

    fun Context.initGeniusScanSdk() = true

    fun getScanFlowContract(): ActivityResultContract<InputType, OutputType>

    fun Activity.startScanFlow(resultLauncher: ActivityResultLauncher<InputType>) {
        MaterialAlertDialogBuilder(this, R.style.DialogStyle)
            .setTitle(R.string.allErrorFeatureNotAvailableInFdroid)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> }
            .show()
    }

    fun Activity.scanResultProcessing(result: OutputType, folder: File?) = Unit
}
