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

import android.app.job.JobInfo
import android.app.job.JobInfo.TriggerContentUri
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.UploadWorker.Companion.showSyncConfigNotification
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.lang.Runnable

@RequiresApi(api = Build.VERSION_CODES.N)
class FileObserveServiceApi24 : JobService() {
    private var runningParams: JobParameters? = null

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val worker = Runnable {
        scheduleJob(this@FileObserveServiceApi24)
        runningParams?.let {
            jobFinished(it, false)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.d("MediaContentJob", "$TAG> JOB STARTED!")
        runningParams = params
        params.triggeredContentAuthorities?.let { _ ->
            if (!applicationContext.isSyncActive()) {
                when {
                    MediaFolder.getAllSyncedFoldersCount() > 0 -> syncImmediately()
                    else -> baseContext.showSyncConfigNotification()
                }
            }
        } ?: Log.d("MediaContentJob", "$TAG> no content")
        handler.post(worker)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        handler.removeCallbacks(worker)
        Log.d("MediaContentJob", "$TAG > JOB STOPPED")
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    companion object {
        private const val TAG = "FileObserveServiceApi24"
        private const val MEDIA_CONTENT_JOB = 1

        fun scheduleJob(context: Context) {
            Log.d("MediaContentJob", "JOB INIT!")
            val syncSetting = UploadFile.getAppSyncSettings()
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            val builder = JobInfo.Builder(
                MEDIA_CONTENT_JOB,
                ComponentName(context, FileObserveServiceApi24::class.java)
            ).setTriggerContentMaxDelay(0)
                .setTriggerContentUpdateDelay(1000) // Waiting time when a new change is detected while this job is scheduled

            if (syncSetting == null) {
                Sentry.captureMessage("FileObserveServiceApi24: disableAutoSync")
                runBlocking(Dispatchers.IO) { context.disableAutoSync() }
                return
            }

            if (syncSetting.syncVideo) {
                builder.addTriggerContentUri(
                    TriggerContentUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)
                )
            }

            builder.addTriggerContentUri(
                TriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)
            )

            jobScheduler.schedule(builder.build())
            Log.d("MediaContentJob", "JOB SCHEDULED!")
        }

        fun cancelJob(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(MEDIA_CONTENT_JOB)
        }
    }
}