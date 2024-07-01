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
package com.infomaniak.drive.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infomaniak.drive.ui.MainViewModel

class DownloadReceiver(private val mainViewModel: MainViewModel) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        val fileId = intent.getIntExtra(CANCELLED_FILE_ID, 0)

        if (fileId > 0 || intent.action == BulkDownloadWorker.TAG) {
            mainViewModel.updateVisibleFiles.value = true
        }
    }

    companion object {
        const val TAG = "DownloadReceiver"
        const val CANCELLED_FILE_ID = "CANCELLED_FILE_ID"
    }
}
