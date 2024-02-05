/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive.data.services

import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import com.infomaniak.drive.data.models.IpsToken
import com.infomaniak.drive.data.models.MqttAction
import com.infomaniak.drive.data.models.MqttNotification
import com.infomaniak.drive.utils.BulkOperationsUtils.isBulkOperationActive
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.lib.core.api.ApiController.gson
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.Utils
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*

object MqttClientWrapper : MqttCallback, LiveData<MqttNotification>() {

    private const val MQTT_AUTO_DISCONNECT_TIMER = 5_000L

    private lateinit var appContext: Context
    private lateinit var clientId: String
    private lateinit var options: MqttConnectOptions
    private lateinit var timer: CountDownTimer
    private var currentToken: IpsToken? = null
    private var isSubscribed: Boolean = false
    private var runningExternalImportIds: MutableSet<Int> = mutableSetOf()

    private const val MQTT_USER = "ips:ips-public"
    private const val MQTT_PASS = "8QC5EwBqpZ2Z" // Yes it's normal, non-sensitive information
    private const val MQTT_URI = "wss://info-mq.infomaniak.com/ws"

    private val client: MqttAndroidClient by lazy { MqttAndroidClient(appContext, MQTT_URI, clientId) }

    fun init(context: Context) {

        fun generateClientId(): String {
            return "mqtt_android_kdrive_" + buildString { repeat(10) { append(('a'..'z').random()) } }
        }

        appContext = context
        clientId = generateClientId()

        client.setCallback(this)
        options = MqttConnectOptions()
        options.userName = MQTT_USER
        options.password = MQTT_PASS.toCharArray()
        options.keepAliveInterval = 30
        options.isAutomaticReconnect = true
    }

    fun updateToken(token: IpsToken) {
        if (token.uuid != currentToken?.uuid) {
            if (isSubscribed) {
                currentToken?.let { unsubscribe(topicFor(it)) }
                subscribe(topicFor(token))
            }
            currentToken = token
        }
    }

    fun start(
        externalImportId: Int? = null,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        completion: () -> Unit = {},
    ) {
        externalImportId?.let { runningExternalImportIds.add(it) }

        // If we are already connected, just run the BulkOperation immediately
        if (client.isConnected) {
            coroutineScope.launch {
                completion()
            }
            return
        }

        try {
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    SentryLog.i("MQTT connection", "Success : true")
                    coroutineScope.launch {
                        currentToken?.let {
                            subscribe(topicFor(it))
                            completion()

                        }

                        // If there is no more active worker, stop MQTT
                        timer = Utils.createRefreshTimer(milliseconds = MQTT_AUTO_DISCONNECT_TIMER) {
                            if (appContext.isBulkOperationActive() || runningExternalImportIds.size.isPositive()) {
                                timer.start()
                            } else {
                                currentToken?.let { unsubscribe(topicFor(it)) }
                                client.disconnect()
                            }
                        }
                        timer.start()
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    exception?.printStackTrace()
                    SentryLog.i("MQTT connection", "Success : false")
                }

            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    // QoS 0 to have auto-delete queues
    private fun subscribe(topic: String, qos: Int = 0) {
        client.subscribe(topic, qos, null, null)
        isSubscribed = true
    }

    private fun unsubscribe(topic: String) {
        client.unsubscribe(topic, null, null)
        isSubscribed = false
    }

    private fun topicFor(token: IpsToken): String {
        return "drive/${token.uuid}"
    }

    override fun connectionLost(cause: Throwable?) {
        SentryLog.e("MQTT Error", "Connection has been lost. Stacktrace below.")
        cause?.printStackTrace()
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        with(gson.fromJson(message.toString(), MqttNotification::class.java)) {
            if (action == MqttAction.EXTERNAL_IMPORT_FINISH || action == MqttAction.EXTERNAL_IMPORT_CANCEL) {
                runningExternalImportIds.remove(importId)
            }

            postValue(this)
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
}
