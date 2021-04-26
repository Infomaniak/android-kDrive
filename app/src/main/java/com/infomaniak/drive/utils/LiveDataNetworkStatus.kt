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
package com.infomaniak.drive.utils

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import java.net.UnknownHostException

class LiveDataNetworkStatus(context: Context) : LiveData<Boolean>() {

    companion object {
        const val ROOT_SERVER_CHECK_URL = "a.root-servers.net"
    }

    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networks: ArrayList<Network> = ArrayList()

    private val networkStateObject = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            super.onLost(network)
            networks.remove(network)
            postValue(!networks.all { !checkInternetConnectivity(it) })
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            networks.add(network)
            postValue(checkInternetConnectivity(network))
        }

        fun checkInternetConnectivity(network: Network): Boolean {
            return try {
                network.getByName(ROOT_SERVER_CHECK_URL) != null
            } catch (e: UnknownHostException) {
                false
            }
        }
    }

    override fun onActive() {
        super.onActive()
        connectivityManager.registerNetworkCallback(networkRequestBuilder(), networkStateObject)
        postValue(false) // Consider all networks "unavailable" on start of listening
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(networkStateObject)
    }

    private fun networkRequestBuilder(): NetworkRequest {
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
    }
}