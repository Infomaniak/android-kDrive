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

import android.app.Application
import com.infomaniak.drive.ApplicationMain.Companion.tracker
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.DownloadTracker
import org.matomo.sdk.extra.TrackHelper

object MatomoUtils {

    fun buildTracker() = TrackerBuilder("https://analytics.infomaniak.com/matomo.php", 8, "AndroidTracker")

    fun Application.trackDownloads() {
        TrackHelper.track().download().identifier(DownloadTracker.Extra.ApkChecksum(this)).with(tracker)
    }

    fun Application.trackEvent(category: String, action: String, name: String? = null) {
        TrackHelper.track().event(category, action).name(name).with(tracker)
    }

    fun Application.trackScreen(path: String, title: String) {
        TrackHelper.track().screen(path).title(title).with(tracker)
    }

    fun Application.trackCurrentUserId() {
        tracker.userId = AccountUtils.currentUserId.toString()
    }
}