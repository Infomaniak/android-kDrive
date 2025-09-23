/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.lib.core.R
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.showToast

object PublicShareUtils {

    fun launchDeeplink(activity: Activity, deeplink: String, shouldFinish: Boolean) {
        Intent(activity, LaunchActivity::class.java).apply {
            setData(deeplink.toUri())
            clearStack()
        }.also(activity::startActivity)
        if (shouldFinish) activity.finishAffinity()
    }

    fun openDeepLinkInBrowser(activity: Activity, url: String) = runCatching {
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).apply {
            setData(url.toUri())
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        }.also(activity::startActivity)
        activity.finishAndRemoveTask()
    }.onFailure { exception ->
        SentryLog.e("OpenDeepLinkInBrowser", exception.message.toString(), exception)
        val errorMessage = if (exception is ActivityNotFoundException) {
            R.string.browserNotFound
        } else {
            R.string.anErrorHasOccurred
        }
        activity.showToast(errorMessage)
    }
}
