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
package com.infomaniak.drive

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.util.CoilUtils
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.infomaniak.drive.BuildConfig.DRIVE_API
import com.infomaniak.drive.BuildConfig.INFOMANIAK_API
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider.Companion.initRealm
import com.infomaniak.drive.data.models.UISettings
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.data.sync.UploadNotifications.pendingIntentFlags
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.NotificationUtils.initNotificationChannel
import com.infomaniak.drive.utils.NotificationUtils.showGeneralNotification
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.auth.TokenAuthenticator
import com.infomaniak.lib.core.auth.TokenInterceptor
import com.infomaniak.lib.core.auth.TokenInterceptorListener
import com.infomaniak.lib.core.models.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.login.ApiToken
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.eclipse.paho.client.mqttv3.*
import java.util.*

class ApplicationMain : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(UISettings(this).nightMode)

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
            StrictMode.setVmPolicy(
                VmPolicy.Builder().apply {
                    detectActivityLeaks()
                    detectLeakedClosableObjects()
                    detectLeakedRegistrationObjects()
                    detectFileUriExposure()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectContentUriWithoutPermission()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) detectCredentialProtectedWhileLocked()
                }.build()
            )
        }

        SentryAndroid.init(this) { options: SentryAndroidOptions ->
            // register the callback as an option
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent?, _: Any? ->
                //if the application is in debug mode discard the events
                if (BuildConfig.DEBUG) null else event
            }
        }

        runBlocking { initRealm() }

        AccountUtils.reloadApp = {
            startActivity(Intent(this, LaunchActivity::class.java).clearStack())
        }

        InfomaniakCore.init(
            appVersionName = BuildConfig.VERSION_NAME,
            clientID = BuildConfig.CLIENT_ID,
            credentialManager = null,
            isDebug = BuildConfig.DEBUG
        )

        KDriveHttpClient.onRefreshTokenError = refreshTokenError
        initNotificationChannel()
        HttpClient.init(tokenInterceptorListener())
        MqttClientWrapper.init(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .componentRegistry {
                add(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoderDecoder(applicationContext) else GifDecoder())
            }
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    addInterceptor(Interceptor { chain ->
                        var request = chain.request()
                        if (request.url.toString().contains(DRIVE_API) || request.url.toString().contains(INFOMANIAK_API)) {
                            request = request.newBuilder().headers(HttpUtils.getHeaders()).removeHeader("Cache-Control").build()
                        }
                        chain.proceed(request)
                    })
                    addInterceptor(TokenInterceptor(tokenInterceptorListener()))
                    authenticator(TokenAuthenticator(tokenInterceptorListener()))
                    if (com.infomaniak.lib.core.BuildConfig.DEBUG) {
                        addNetworkInterceptor(StethoInterceptor())
                    }
                    cache(CoilUtils.createDefaultCache(applicationContext))
                }.build()
            }
            .build()
    }

    private val refreshTokenError: (User) -> Unit = { user ->
        val openAppIntent = Intent(this, LaunchActivity::class.java).clearStack()
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingIntentFlags)
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        showGeneralNotification(getString(R.string.refreshTokenError)).apply {
            setContentIntent(pendingIntent)
            notificationManagerCompat.notify(UUID.randomUUID().hashCode(), build())
        }

        CoroutineScope(Dispatchers.IO).launch {
            AccountUtils.removeUser(this@ApplicationMain, user)
        }
    }

    private fun tokenInterceptorListener() = object : TokenInterceptorListener {
        override suspend fun onRefreshTokenSuccess(apiToken: ApiToken) {
            AccountUtils.setUserToken(AccountUtils.currentUser!!, apiToken)
        }

        override suspend fun onRefreshTokenError() {
            refreshTokenError(AccountUtils.currentUser!!)
        }

        override suspend fun getApiToken(): ApiToken {
            return AccountUtils.currentUser!!.apiToken
        }
    }

}
