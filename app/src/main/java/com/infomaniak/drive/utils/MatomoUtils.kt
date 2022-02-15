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

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.infomaniak.drive.ApplicationMain
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.SyncSettings
import org.matomo.sdk.Matomo
import org.matomo.sdk.Tracker
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.DownloadTracker
import org.matomo.sdk.extra.TrackHelper
import java.util.*

object MatomoUtils {

    private inline val Context.tracker: Tracker get() = (this as ApplicationMain).matomoTracker
    private const val siteId = 8

    fun Context.buildTracker(): Tracker {
        return TrackerBuilder(BuildConfig.MATOMO_URL, siteId, "AndroidTracker").build(Matomo.getInstance(this)).also {
            // Track app version installs
            TrackHelper.track().download().identifier(DownloadTracker.Extra.ApkChecksum(this)).with(it)
        }
    }

    fun Context.trackCurrentUserId() {
        tracker.userId = AccountUtils.currentUserId.toString()
    }

    fun Context.trackScreen(path: String, title: String) {
        TrackHelper.track().screen(path).title(title).with(tracker)
    }

    fun Activity.trackScreen() {
        TrackHelper.track().screen(this).title(this::class.java.simpleName).with(application.tracker)
    }

    fun Fragment.trackScreen() {
        context?.applicationContext?.trackScreen(this::class.java.name, this::class.java.simpleName)
    }

    fun Context.trackEvent(category: String, action: TrackerAction, trackerName: String? = null, trackerValue: Float? = null) {
        TrackHelper.track().event(category, action.toString()).name(trackerName).value(trackerValue).with(tracker)
    }

    fun Activity.trackEvent(category: String, action: TrackerAction, trackerName: String? = null, trackerValue: Float? = null) {
        application.trackEvent(category, action, trackerName, trackerValue)
    }

    fun Fragment.trackEvent(category: String, action: TrackerAction, trackerName: String? = null, trackerValue: Float? = null) {
        context?.applicationContext?.trackEvent(category, action, trackerName, trackerValue)
    }

    fun Context.trackBulkActionEvent(action: BulkOperationType, modifiedFileNumber: Int) {
        val trackerName = "bulk" + if (modifiedFileNumber == 1) "Single" else "" + action.toString()
        trackEvent("FileAction", TrackerAction.CLICK, trackerName, modifiedFileNumber.toFloat())
    }

    fun Context.trackEventWithBooleanValue(category: String, trackerName: String, trackerValue: Boolean?) {
        trackEvent(category, TrackerAction.CLICK, trackerName, trackerValue?.toFloat())
    }

    fun Context.trackPhotoSyncSettingsEvent(syncSettings: SyncSettings, trackingDateName: String) {
        val category = "photoSync"
        trackEventWithBooleanValue(category, "deleteAfterImport", syncSettings.deleteAfterSync)
        trackEventWithBooleanValue(category, "createDatedFolders", syncSettings.createDatedSubFolders)
        trackEventWithBooleanValue(category, "importVideo", syncSettings.syncVideo)
        trackEvent(category, TrackerAction.CLICK, trackingDateName)
    }

    fun Context.trackShareRightsEvent(trackerName: String) {
        trackEvent("shareAndRights", TrackerAction.CLICK, trackerName)
    }

    fun Context.trackShareSettingsEvent(protectWithPassword: Boolean?, expirationDate: Boolean?, downloadFromLink: Boolean?) {
        val category = "shareAndRights"
        trackEventWithBooleanValue(category, "protectWithPassword", protectWithPassword)
        trackEventWithBooleanValue(category, "expirationDateLink", expirationDate)
        trackEventWithBooleanValue(category, "downloadFromLink", downloadFromLink)
    }

    fun Context.trackFileActionEvent(trackerName: String, trackerValue: Float? = null) {
        trackEvent("fileAction", TrackerAction.CLICK, trackerName, trackerValue)
    }

    fun Activity.trackAccountEvent(trackerName: String) {
        trackEvent("account", TrackerAction.CLICK, trackerName)
    }

    fun Fragment.trackCategoriesEvent(trackerName: String) {
        trackEvent("categories", TrackerAction.CLICK, trackerName)
    }

    fun Fragment.trackNewElementEvent(trackerName: String) {
        trackEvent("newElement", TrackerAction.CLICK, trackerName)
    }

    fun Fragment.trackTrashEvent(name: String) {
        trackEvent("trash", TrackerAction.CLICK, name)
    }
}

enum class TrackerAction {
    CLICK, INPUT;

    override fun toString() = name.lowercase(Locale.getDefault())
}
