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
package com.infomaniak.drive.utils

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.sync.UploadNotifications
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.ui.login.MigrationActivity.Companion.getOldkDriveUser
import com.infomaniak.drive.utils.NotificationUtils.showGeneralNotification
import com.infomaniak.drive.utils.SyncUtils.activateSyncIfNeeded
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.hasPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        with(context) {

            if (!getOldkDriveUser().isEmpty) {
                val openAppIntent = Intent(this, LaunchActivity::class.java).clearStack()
                val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, UploadNotifications.pendingIntentFlags)
                val notificationManagerCompat = NotificationManagerCompat.from(context)

                showGeneralNotification(getString(R.string.migrateNotificationTitle)).apply {
                    setContentText(getString(R.string.migrateNotificationDescription))
                    setContentIntent(pendingIntent)
                    notificationManagerCompat.notify(UUID.randomUUID().hashCode(), build())
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                MediaFolder.getRealmInstance().use { realm ->
                    if (context.hasPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) &&
                        MediaFolder.getAllCount(realm) == 0L
                    ) {
                        ArrayList(MediaFoldersProvider.getAllMediaFolders(realm, contentResolver))
                    }
                }
            }

            activateSyncIfNeeded()

            startContentObserverService()
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED && AccountUtils.isEnableAppSync()) syncImmediately()
        }
    }
}
