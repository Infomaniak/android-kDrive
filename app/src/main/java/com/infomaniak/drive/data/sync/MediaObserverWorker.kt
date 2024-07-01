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
package com.infomaniak.drive.data.sync

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.*
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.UploadWorker.Companion.showSyncConfigNotification
import com.infomaniak.drive.data.sync.MediaObserverService.Companion.TRIGGER_CONTENT_DELAY
import com.infomaniak.drive.utils.MediaFoldersProvider
import com.infomaniak.drive.utils.NotificationUtils.fileObserveServiceNotification
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.SentryLog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@RequiresApi(api = Build.VERSION_CODES.N)
class MediaObserverWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        SentryLog.d("MediaContentJob", "$TAG> JOB STARTED!")

        if (!applicationContext.isSyncActive()) {
            when {
                MediaFolder.getAllSyncedFoldersCount() > 0 -> applicationContext.syncImmediately()
                else -> applicationContext.showSyncConfigNotification()
            }
        }

        nextScheduleWork(applicationContext)
        SentryLog.d("MediaContentJob", "$TAG> JOB FINISHED!")
        Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.fileObserveServiceNotification().build()
        return ForegroundInfo(TAG.hashCode(), notification)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    companion object {
        private const val TAG = "MediaObserverWorker"

        fun scheduleWork(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MediaObserverWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun nextScheduleWork(context: Context) {
            SentryLog.d("MediaContentJob", "JOB INIT!")
            val syncSetting = UploadFile.getAppSyncSettings()

            if (syncSetting == null) {
                Sentry.captureMessage("$TAG: disableAutoSync")
                context.disableAutoSync()
                return
            }

            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaFoldersProvider.imagesExternalUri, true)
                .setTriggerContentMaxDelay(TRIGGER_CONTENT_DELAY, TimeUnit.MILLISECONDS)
                .setTriggerContentUpdateDelay(TRIGGER_CONTENT_DELAY, TimeUnit.MILLISECONDS)
                .apply {
                    if (syncSetting.syncVideo) {
                        addContentUriTrigger(MediaFoldersProvider.videosExternalUri, true)
                    }
                }
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MediaObserverWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
