/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.extensions

import android.annotation.SuppressLint
import androidx.navigation.NavDestination
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.MatomoDrive.trackScreen
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

@SuppressLint("RestrictedApi")
fun NavDestination.addSentryBreadcrumb() {
    Sentry.addBreadcrumb(Breadcrumb().apply {
        category = "Navigation"
        message = "Accessed to destination : $displayName"
        level = SentryLevel.INFO
    })
}

@SuppressLint("RestrictedApi")
fun NavDestination.trackDestination() {
    trackScreen(
        path = displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"),
        title = label.toString(),
    )
}
