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
package com.infomaniak.drive

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import org.matomo.sdk.Tracker

object MatomoDrive : MatomoCore {

    override val Context.tracker: Tracker get() = (this as MainApplication).matomoTracker
    override val siteId = 8

    const val ACTION_DOWNLOAD_NAME = "download"
    const val ACTION_OPEN_WITH_NAME = "openWith"
    const val ACTION_OPEN_BOOKMARK_NAME = "openBookmark"
    const val ACTION_PRINT_PDF_NAME = "printPdf"
    const val ACTION_SAVE_TO_KDRIVE_NAME = "saveToKDrive"
    const val ACTION_SEND_FILE_COPY_NAME = "sendFileCopy"

    fun Fragment.trackCategoriesEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("categories", name, action, value)
    }

    fun Context.trackFileActionEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("fileAction", name, action, value)
    }

    fun Context.trackPublicShareActionEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("publicShareAction", name, action, value)
    }

    fun Context.trackPdfActivityActionEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("pdfActivityAction", name, action, value)
    }

    fun Fragment.trackShareRightsEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackShareRightsEvent(name, action, value)
    }

    fun Context.trackShareRightsEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("shareAndRights", name, action, value)
    }

    fun Fragment.trackNewElementEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        context?.trackNewElementEvent(name, action, value)
    }

    fun Context.trackNewElementEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("newElement", name, action, value)
    }

    fun Fragment.trackTrashEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("trash", name, action, value)
    }

    fun Activity.trackInAppUpdate(name: String) {
        trackEvent("inAppUpdate", name)
    }

    fun Activity.trackInAppReview(name: String) {
        trackEvent("inAppReview", name)
    }

    fun Activity.trackDeepLink(name: String) {
        trackEvent("deepLink", name)
    }
}
