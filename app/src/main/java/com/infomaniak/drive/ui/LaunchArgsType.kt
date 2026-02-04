/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui

import android.content.Intent

sealed interface LaunchArgsType {
    @JvmInline
    value class Notification(val navArgs: LaunchActivityArgs) : LaunchArgsType
    @JvmInline
    value class Shortcut(val tag: String) : LaunchArgsType
    @JvmInline
    value class Deeplink(val uriPath: String) : LaunchArgsType
    companion object {
        private const val SHORTCUTS_TAG = "shortcuts_tag"
        fun from(navigationArgs: LaunchActivityArgs?, intent: Intent): LaunchArgsType? {
            val navArgs = navigationArgs?.takeUnless { it.destinationUserId == 0 || it.destinationDriveId == 0 }
            if (navArgs != null) return Notification(navArgs)

            val shortcut = intent.getStringExtra(SHORTCUTS_TAG)
            if (shortcut != null) return Shortcut(shortcut)

            val uriPath = intent.data?.path
            if (uriPath != null) return Deeplink(uriPath)

            return null
        }
    }

}
