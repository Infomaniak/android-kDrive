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
package com.infomaniak.drive

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.infomaniak.core.notifications.registration.NotificationsRegistrationManager
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.twofactorauth.back.notifications.TwoFactorAuthNotifications

class DriveMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        SentryLog.i(TAG, "onNewToken: new token received")
        NotificationsRegistrationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isNotEmpty()) {
            SentryLog.d(TAG, "onMessageReceived: Message data payload size: " + message.data.size)
        }
        val type = message.data["type"]
        SentryLog.i(TAG, "onMessageReceived: type=$type")

        when (type) {
            TwoFactorAuthNotifications.TYPE -> twoFactorAuthManager.onApprovalChallengePushed(
                remoteMessageData = message.data,
                remoteMessageSentTimeUtcMillis = message.sentTime,
                remoteMessageTimeToLiveSeconds = message.ttl // Note: could be 0 if not set.
            )
            else -> SentryLog.e(TAG, "Unexpected notification type")
        }
    }

    companion object {
        private const val TAG = "DriveMessagingService"
    }
}
