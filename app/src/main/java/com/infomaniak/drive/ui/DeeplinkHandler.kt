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

import android.net.Uri
import android.os.Bundle
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackDeepLink
import com.infomaniak.drive.data.models.deeplink.ACTION
import com.infomaniak.drive.data.models.deeplink.ACTION_TYPE
import com.infomaniak.drive.data.models.deeplink.DeeplinkAction
import com.infomaniak.drive.data.models.deeplink.DeeplinkType
import com.infomaniak.drive.data.models.deeplink.InvalidValue

class DeeplinkHandler {
    private var deeplinkType: DeeplinkType? = null

    private val actionRegex by lazy { Regex("app/$ACTION_TYPE/$ACTION") }

    fun handle(uri: Uri, deeplink: String) {
        deeplinkType = try {
            actionRegex.find(deeplink)?.run {
                val (actionType, action) = destructured
                DeeplinkAction.from(originalUri = uri, actionType = actionType, action = action)
            }
        } catch (_: InvalidValue) {
            DeeplinkType.Invalid(originalUri = uri)
        }
        trackDeepLink(MatomoName.Internal)
    }

    fun forceFail() {
        deeplinkType = DeeplinkType.Invalid(originalUri = Uri.EMPTY)
    }

    val extras: Bundle?
        get() = deeplinkType?.let { MainActivityArgs(deeplinkType = deeplinkType).toBundle() }

    fun notHandledUri() = deeplinkType?.takeUnless { it.isHandled }?.originalUri
}
