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

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.sync.UploadAdapter.Companion.showSyncConfigNotification
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.SyncUtils.syncDelayJob
import kotlinx.coroutines.Job


class FileObserveService : Service() {
    private lateinit var tableObserver: TableObserver
    private var syncJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("kDrive", "$TAG > started")
        isRunning = true
        initial()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initial() {
        tableObserver = TableObserver(null)
        val syncSetting = UploadFile.getAppSyncSettings()!!

        if (syncSetting.syncVideo) {
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, tableObserver)
            contentResolver.registerContentObserver(MediaStore.Video.Media.INTERNAL_CONTENT_URI, true, tableObserver)
        }

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, tableObserver)
        contentResolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true, tableObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        contentResolver.unregisterContentObserver(tableObserver)
        Log.d("kDrive", "$TAG > destroyed")
    }

    private inner class TableObserver(handler: Handler?) : ContentObserver(handler) {

        /**
         * Returns true if this observer is interested receiving self-change notifications.
         */
        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Log.d(TAG, "URL : " + uri.toString())

            uri?.let {
                if (!applicationContext.isSyncActive()) {
                    when {
                        MediaFolder.getAllSyncedFoldersCount() > 0 -> {
                            syncJob?.cancel()
                            syncJob = applicationContext.syncDelayJob()
                        }
                        else -> showSyncConfigNotification(baseContext)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "FileObserveService"
        var isRunning = false
    }
}