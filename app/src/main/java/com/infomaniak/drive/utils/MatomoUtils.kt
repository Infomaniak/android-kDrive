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

import android.content.Context
import android.util.Log
import com.infomaniak.drive.ApplicationMain.Companion.tracker
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.SyncSettings
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.DownloadTracker
import org.matomo.sdk.extra.TrackHelper

object MatomoUtils {

    fun buildTracker() = TrackerBuilder("https://analytics.infomaniak.com/matomo.php", 8, "AndroidTracker")

    fun Context.trackDownloads() {
        TrackHelper.track().download().identifier(DownloadTracker.Extra.ApkChecksum(this)).with(tracker)
    }

    fun Context.trackEvent(category: String, action: String, name: String? = null, value: Float? = null) {
        TrackHelper.track().event(category, action).name(name).value(value).with(tracker)
    }

    fun Context.trackBulkActionEvent(action: BulkOperationType, value: Int) {
        var name = "bulk"
        if (value == 1) {
            name += "Single"
        }

        val trackingActionName = when (action) {
            BulkOperationType.ADD_FAVORITES -> "Favorite"
            BulkOperationType.COPY -> "Copy"
            BulkOperationType.MOVE -> "Move"
            BulkOperationType.SET_OFFLINE -> "Offline"
            BulkOperationType.TRASH -> "PutInTrash"
            BulkOperationType.COLOR_FOLDER -> "ColorFolder"
        }
        trackEvent("FileAction", "click", name + trackingActionName, value.toFloat())
    }

    fun Context.trackEventWithBooleanValue(category: String, name: String, value: Boolean) {
        trackEvent(category, "click", name, value.toFloat())
    }

    fun Context.trackScreen(path: String, title: String) {
        TrackHelper.track().screen(path).title(title).with(tracker)
    }

    fun Context.trackCurrentUserId() {
        tracker.userId = AccountUtils.currentUserId.toString()
    }

    fun Context.addTrackingCallbackForDebugLog() {
        tracker.addTrackingCallback { trackMe ->
            trackMe.apply {
                toMap().forEach() {
                    when (it.key) {
                        "action_name" -> Log.d("TRACKER_SCREEN", it.value)
                        "e_c" -> Log.d("TRACKER_EVENT", "Category: ${it.value}")
                        "e_a" -> Log.d("TRACKER_EVENT", "Action: ${it.value}")
                        "e_n" -> Log.d("TRACKER_EVENT", "Name: ${it.value}")
                        "e_v" -> Log.d("TRACKER_EVENT", "Value: ${it.value}")
                    }
                }
            }
        }
    }

    fun Context.trackPhotoSyncSettings(syncSettings: SyncSettings, trackingDateName: String) {
        trackEventWithBooleanValue("photoSync", "deleteAfterImport", syncSettings.deleteAfterSync)
        trackEventWithBooleanValue("photoSync", "createDatedFolders", syncSettings.createDatedSubFolders)
        trackEventWithBooleanValue("photoSync", "importVideo", syncSettings.syncVideo)
        trackEvent("photoSync", "click", trackingDateName)
    }

    fun Context.trackShareSettings(protectWithPassword: Boolean, expirationDate: Boolean, downloadFromLink: Boolean) {
        trackEventWithBooleanValue("shareAndRights", "protectWithPassword", protectWithPassword)
        trackEventWithBooleanValue("shareAndRights", "expirationDateLink", expirationDate)
        trackEventWithBooleanValue("shareAndRights", "downloadFromLink", downloadFromLink)
    }

    private fun Boolean.toFloat() = if (this) 1f else 0f
}