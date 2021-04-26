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
package com.infomaniak.drive.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infomaniak.drive.data.models.FileInProgress
import com.infomaniak.drive.ui.MainViewModel

class UploadProgressReceiver(private val mainViewModel: MainViewModel) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val fileInProgress = intent.getParcelableExtra<FileInProgress>(UploadAdapter.IMPORT_IN_PROGRESS)
        val operationStatus = intent.getParcelableExtra<UploadAdapter.ProgressStatus>(UploadAdapter.OPERATION_STATUS)

        when (fileInProgress?.status) {
            UploadAdapter.ProgressStatus.RUNNING, UploadAdapter.ProgressStatus.FINISHED -> {
                mainViewModel.fileInProgress.value = fileInProgress
            }
            UploadAdapter.ProgressStatus.STARTED -> {
                mainViewModel.refreshActivities.value = true
            }
            else -> Unit
        }

        if (operationStatus == UploadAdapter.ProgressStatus.FINISHED) mainViewModel.refreshActivities.value = true

    }

    companion object {
        const val TAG = "UploadProgressReceiver"
    }
}