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
package com.infomaniak.drive.data.services

import android.content.Context
import androidx.work.*
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import java.util.concurrent.TimeUnit

class PeriodicUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!applicationContext.isSyncActive()) {
            applicationContext.syncImmediately()
        }
        return Result.success()
    }

    companion object {
        val TAG: String = PeriodicUploadWorker::class.java.simpleName

        fun scheduleWork(context: Context, syncInterval: Long) {
            val request = PeriodicWorkRequestBuilder<PeriodicUploadWorker>(syncInterval, TimeUnit.SECONDS)
                .setConstraints(UploadWorker.workConstraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }
}