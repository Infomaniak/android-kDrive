/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

object MqttClientWrapper {

    private lateinit var appContext: Context
    private lateinit var clientId: String

    private const val MQTT_USER = "ips:ips-public"
    private const val MQTT_PASS = "8QC5EwBqpZ2Z"
    private const val MQTT_URI = "wss://info-mq.infomaniak.com/ws"

    private val client: MqttAndroidClient by lazy {
        MqttAndroidClient(appContext, MQTT_URI, clientId)
    }

    fun init(context: Context, clientId: String = "", callback: MqttCallback, onClientConnected: (success: Boolean) -> Unit) {
        this.appContext = context
        this.clientId = clientId

        client.setCallback(callback)
        val options = MqttConnectOptions()
        options.userName = MQTT_USER
        options.password = MQTT_PASS.toCharArray()

        try {
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onClientConnected(true)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    exception?.printStackTrace()
                    onClientConnected(false)
                }

            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun subscribe(topic: String, qos: Int = 1, listener: IMqttActionListener) {
        client.subscribe(topic, qos, null, listener)
    }

    fun unsubscribe(topic: String, listener: IMqttActionListener) {
        client.unsubscribe(topic, null, listener)
    }

    fun publish(topic: String, msg: String, qos: Int = 1, retained: Boolean = false, listener: IMqttActionListener) {
        val message = MqttMessage()
        message.payload = msg.toByteArray()
        message.qos = qos
        message.isRetained = retained
        client.publish(topic, message, null, listener)
    }

    fun disconnect(listener: IMqttActionListener) {
        client.disconnect(null, listener)
    }
}