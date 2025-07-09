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
import com.infomaniak.core.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.capitalizeFirstChar
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

    const val PUBLIC_SHARE_ACTION_CATEGORY = "publicShareAction"

    fun Fragment.trackCategoriesEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("categories", name, action, value)
    }

    fun Context.trackFileActionEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("fileAction", name, action, value)
    }

    fun Context.trackPublicShareActionEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(PUBLIC_SHARE_ACTION_CATEGORY, name, action, value)
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

    fun Fragment.trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun Context.trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun Context.trackMyKSuiteUpgradeBottomSheetEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE_UPGRADE_BOTTOMSHEET, name)
    }

    fun Fragment.trackDropboxEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("dropbox", name, action, value)
    }

    fun Fragment.trackSearchEvent(name: String) {
        trackEvent("search", name)
    }

    fun Fragment.trackCommentEvent(name: String) {
        trackEvent("comment", name)
    }

    fun Fragment.trackBulkActionEvent(category: String, action: BulkOperationType, modifiedFileNumber: Int) {

        fun BulkOperationType.toMatomoString(): String = name.lowercase().capitalizeFirstChar()

        val name = "bulk" + (if (modifiedFileNumber == 1) "Single" else "") + action.toMatomoString()
        trackEvent(category, name, value = modifiedFileNumber.toFloat())
    }

    fun Fragment.trackMediaPlayerEvent(name: String, value: Float? = null) {
        trackEvent("mediaPlayer", name, value = value)
    }

    fun Fragment.trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent("settings", name, value = value?.toFloat())
    }

    fun Context.trackPhotoSyncEvent(name: String, value: Boolean? = null) {
        trackEvent("photoSync", name, value = value?.toFloat())
    }
}
