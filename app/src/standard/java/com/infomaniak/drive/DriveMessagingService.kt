package com.infomaniak.drive

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.infomaniak.lib.core.utils.SentryLog

class DriveMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        SentryLog.d("onNewToken", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            SentryLog.d("onMessageReceived", "Message data payload: " + remoteMessage.data)
        }
    }
}
