package com.infomaniak.drive

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DriveMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("onNewToken", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("onMessageReceived", "Message data payload: " + remoteMessage.data)
        }
    }
}