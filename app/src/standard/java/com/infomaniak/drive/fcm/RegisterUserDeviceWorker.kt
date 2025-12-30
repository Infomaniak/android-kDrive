/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.fcm

import android.content.Context
import androidx.work.WorkerParameters
import com.infomaniak.core.isChannelEnabled
import com.infomaniak.core.isChannelEnabledFlow
import com.infomaniak.core.notifications.registration.AbstractNotificationsRegistrationWorker
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.twofactorauth.back.notifications.TwoFactorAuthNotifications
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import splitties.systemservices.notificationManager

class RegisterUserDeviceWorker(
    appContext: Context,
    params: WorkerParameters
) : AbstractNotificationsRegistrationWorker(appContext, params) {

    override suspend fun getConnectedHttpClient(userId: Int) = AccountUtils.getHttpClient(userId)

    override suspend fun currentTopicsForUser(userId: Int): List<String> {
        val enabled = notificationManager.isChannelEnabled(TwoFactorAuthNotifications.CHANNEL_ID)
        return getNotificationTopicsForUser(enabled)
    }
}

fun notificationTopicsForUser(
    @Suppress("unused") userId: Int // Not used because we don't need user-specific topics & channels yet.
): Flow<List<String>> {
    return notificationManager.isChannelEnabledFlow(
        channelId = TwoFactorAuthNotifications.CHANNEL_ID
    ).map { enabled ->
        getNotificationTopicsForUser(enabled)
    }
}

private fun getNotificationTopicsForUser(canShow2faNotifications: Boolean?): List<String> {
    return when (canShow2faNotifications) {
        true -> listOf(TwoFactorAuthNotifications.TOPIC)
        false -> emptyList()
        null -> {
            SentryLog.wtf(TAG, "The channel was created too late, or was deleted by the app!")
            emptyList()
        }
    }
}

private const val TAG = "RegisterUserDeviceWorker"
