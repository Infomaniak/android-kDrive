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
import android.os.IBinder
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.NotificationUtils
import com.infomaniak.drive.utils.NotificationUtils.uploadServiceNotification

/**
 * Define a Service that returns an [android.os.IBinder] for the
 * sync adapter class, allowing the sync adapter framework to call
 * onPerformSync().
 */
class UploadService : Service() {

    override fun onCreate() {
        uploadServiceNotification().apply {
            setContentTitle(getString(R.string.notificationUploadServiceChannelName))
            startForeground(NotificationUtils.UPLOAD_SERVICE_ID, this.build())
        }
        synchronized(syncAdapterLock) {
            uploadAdapter = uploadAdapter ?: UploadAdapter(applicationContext, true)
        }
    }

    /**
     * Return an object that allows the system to invoke
     * the sync adapter.
     *
     */
    override fun onBind(intent: Intent): IBinder {
        return uploadAdapter?.syncAdapterBinder ?: throw IllegalStateException()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        uploadAdapter?.onSyncCanceled()
    }

    companion object {
        // Storage for an instance of the sync adapter
        private var uploadAdapter: UploadAdapter? = null

        // Object to use as a thread-safe lock
        private val syncAdapterLock = Any()
    }
}