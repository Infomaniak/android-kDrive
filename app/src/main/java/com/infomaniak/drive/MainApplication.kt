/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.facebook.stetho.Stetho
import com.infomaniak.core.auth.AccessTokenUsageInterceptor
import com.infomaniak.core.auth.AuthConfiguration
import com.infomaniak.core.auth.models.user.User
import com.infomaniak.core.auth.networking.HttpClient
import com.infomaniak.core.coil.ImageLoaderProvider
import com.infomaniak.core.common.AssociatedUserDataCleanable
import com.infomaniak.core.common.extensions.clearStack
import com.infomaniak.core.crossapplogin.back.internal.deviceinfo.DeviceInfoUpdateManager
import com.infomaniak.core.inappupdate.AppUpdateScheduler
import com.infomaniak.core.legacy.InfomaniakCore
import com.infomaniak.core.legacy.utils.NotificationUtilsCore.Companion.PENDING_INTENT_FLAGS
import com.infomaniak.core.network.NetworkConfiguration
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.core.network.networking.HttpClientConfig
import com.infomaniak.core.sentry.SentryConfig.configureSentry
import com.infomaniak.core.twofactorauth.back.TwoFactorAuthManager
import com.infomaniak.drive.GeniusScanUtils.initGeniusScanSdk
import com.infomaniak.drive.TokenInterceptorListenerProvider.publicShareTokenInterceptorListener
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.FileDeserialization
import com.infomaniak.drive.data.api.publicshare.PublicShareHttpClient
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider.Companion.initRealm
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.coil.ImageLoaderType
import com.infomaniak.drive.data.services.DeviceInfoUpdateWorker
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.ui.LaunchActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MyKSuiteDataUtils
import com.infomaniak.drive.utils.NotificationUtils.buildGeneralNotification
import com.infomaniak.drive.utils.NotificationUtils.initNotificationChannel
import com.infomaniak.drive.utils.NotificationUtils.notifyCompat
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import splitties.init.injectAsAppCtx
import java.util.UUID

/**
 * Singleton for incoming 2FA (two factor authentication) challenges.
 *
 * Not a ViewModel because the state needs to be scoped for the entire app.
 */
val twoFactorAuthManager = TwoFactorAuthManager { userId -> AccountUtils.getHttpClient(userId) }

@HiltAndroidApp
open class MainApplication : Application(), SingletonImageLoader.Factory, DefaultLifecycleObserver {

    init {
        injectAsAppCtx() // Ensures it is always initialized
    }

    var geniusScanIsReady = false

    private val appUpdateWorkerScheduler by lazy { AppUpdateScheduler(applicationContext) }

    protected val applicationScope = CoroutineScope(Dispatchers.Default + CoroutineName("MainApplication"))

    override fun onCreate() {
        super<Application>.onCreate()

        configureInfomaniakCore()

        userDataCleanableList = listOf<AssociatedUserDataCleanable>(DeviceInfoUpdateManager)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val uiSettings = UiSettings(this)
        AppCompatDelegate.setDefaultNightMode(uiSettings.nightMode)

        applicationScope.launch {
            DeviceInfoUpdateManager.scheduleWorkerOnDeviceInfoUpdate<DeviceInfoUpdateWorker>()
        }

        if (BuildConfig.DEBUG) {
            configureDebugMode()
        } else {
            // For Microsoft Office app. Show File.getCloudAndFileUris()
            StrictMode.setVmPolicy(VmPolicy.Builder().build())
        }

        ApiController.init(typeAdapterList = arrayListOf(File::class.java to FileDeserialization()))
        configureSentry()
        runBlocking { initRealm() }

        geniusScanIsReady = initGeniusScanSdk()

        AccountUtils.reloadApp = { bundle ->
            Intent(this, LaunchActivity::class.java).apply {
                putExtras(bundle)
                clearStack()
                startActivity(this)
            }
        }

        AccountUtils.onRefreshTokenError = refreshTokenError
        initNotificationChannel()
        val tokenInterceptorListener =
            TokenInterceptorListenerProvider.tokenInterceptorListener(refreshTokenError, applicationScope)
        HttpClientConfig.customInterceptors = listOf(
            AccessTokenUsageInterceptor(
                previousApiCall = uiSettings.accessTokenApiCallRecord,
                updateLastApiCall = { uiSettings.accessTokenApiCallRecord = it },
            ),
        )
        HttpClient.init(tokenInterceptorListener)
        PublicShareHttpClient.init(publicShareTokenInterceptorListener(applicationScope))
        MqttClientWrapper.init(applicationContext)

        MyKSuiteDataUtils.initDatabase(this)
    }

    private fun configureInfomaniakCore() {
        // Legacy configuration
        InfomaniakCore.apply {
            init(
                appId = BuildConfig.APPLICATION_ID,
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                clientId = BuildConfig.CLIENT_ID,
            )
            apiErrorCodes = ErrorCode.apiErrorCodes
            accessType = null
        }

        // New modules configuration
        NetworkConfiguration.init(
            appId = BuildConfig.APPLICATION_ID,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
//            apiEnvironment = ApiEnvironment.PreProd
        )

        AuthConfiguration.init(
            appId = BuildConfig.APPLICATION_ID,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            clientId = BuildConfig.CLIENT_ID,
        )
    }

    private fun configureDebugMode() {
        Stetho.initializeWithDefaults(this)

        StrictMode.setVmPolicy(
            VmPolicy.Builder().apply {
                detectActivityLeaks()
                detectLeakedClosableObjects()
                detectLeakedRegistrationObjects()
                detectFileUriExposure()
                detectContentUriWithoutPermission()
                if (SDK_INT >= 29) detectCredentialProtectedWhileLocked()
            }.build()
        )

        MatomoDrive.addTrackingCallbackForDebugLog()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        owner.lifecycleScope.launch { appUpdateWorkerScheduler.cancelWorkIfNeeded() }
    }

    override fun onStop(owner: LifecycleOwner) {
        owner.lifecycleScope.launch { appUpdateWorkerScheduler.scheduleWorkIfNeeded() }
        super.onStop(owner)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return newImageLoader(ImageLoaderType.CurrentUser)
    }

    fun newImageLoader(imageLoaderType: ImageLoaderType): ImageLoader {

        val tokenInterceptorListener = when (imageLoaderType) {
            is ImageLoaderType.CurrentUser -> {
                TokenInterceptorListenerProvider.tokenInterceptorListener(refreshTokenError, applicationScope)
            }
            is ImageLoaderType.SpecificUser -> {
                TokenInterceptorListenerProvider.tokenInterceptorListenerByUserId(refreshTokenError, imageLoaderType.userId)
            }
            is ImageLoaderType.PublicShared -> null
        }

        val factory = if (SDK_INT >= 28) {
            AnimatedImageDecoder.Factory()
        } else {
            GifDecoder.Factory()
        }

        return ImageLoaderProvider.newImageLoader(applicationContext, tokenInterceptorListener, customFactories = listOf(factory))
    }

    private val refreshTokenError: (User) -> Unit = { user ->
        val hashCode = UUID.randomUUID().hashCode()
        val openAppIntent = Intent(this, LaunchActivity::class.java).clearStack()
        val pendingIntent = PendingIntent.getActivity(this, hashCode, openAppIntent, PENDING_INTENT_FLAGS)
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        buildGeneralNotification(getString(R.string.refreshTokenError)).apply {
            setContentIntent(pendingIntent)
            notificationManagerCompat.notifyCompat(this@MainApplication, hashCode, build())
        }

        applicationScope.launch {
            AccountUtils.removeUserAndDeleteToken(this@MainApplication, user)
        }
    }

    private fun configureSentry() {
        this.configureSentry(
            isDebug = BuildConfig.DEBUG,
            isSentryTrackingEnabled = UiSettings(applicationContext).isSentryTrackingEnabled,
        )
    }

    companion object {
        @JvmStatic
        var userDataCleanableList: List<AssociatedUserDataCleanable> = emptyList()
            protected set
    }
}
