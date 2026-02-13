/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infomaniak.core.legacy.utils.hasPermissions
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.utils.SyncUtils.activateSyncIfNeeded
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebootReceiver : BroadcastReceiver() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent?): Unit = with(context) {
        val pendingResult = goAsync()
        coroutineScope.launch(Dispatchers.IO) {
            val syncMediaJob = launch { syncMediaFolders() }

            activateSyncIfNeeded()

            startContentObserverService()
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED && AccountUtils.isEnableAppSync()) {
                syncImmediately(isAutomaticTrigger = true)
            }
            syncMediaJob.join()
            pendingResult.finish()
        }
    }

    private fun Context.syncMediaFolders() {
        MediaFolder.getRealmInstance().use { realm ->
            if (hasPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) &&
                MediaFolder.getAllCount(realm) == 0L
            ) {
                // Sync local media folders with Realm
                MediaFoldersProvider.getAllMediaFolders(realm, contentResolver)
            }
        }
    }
}
