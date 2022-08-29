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
package com.infomaniak.drive.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MatomoUtils.trackAccountEvent
import com.infomaniak.drive.utils.MatomoUtils.trackCurrentUserId
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils.lockLandscapeForSmallScreens
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var infomaniakLogin: InfomaniakLogin

    private val webViewLoginResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        with(result) {
            if (resultCode == RESULT_OK) {
                val authCode = data?.extras?.getString(InfomaniakLogin.CODE_TAG)
                val translatedError = data?.extras?.getString(InfomaniakLogin.ERROR_TRANSLATED_TAG)
                when {
                    translatedError?.isNotBlank() == true -> showError(translatedError)
                    authCode?.isNotBlank() == true -> authenticateUser(authCode)
                    else -> showError(getString(R.string.anErrorHasOccurred))
                }
            } else {
                connectButton?.hideProgress(R.string.connect)
                signInButton.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockLandscapeForSmallScreens()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        infomaniakLogin = InfomaniakLogin(context = this, appUID = BuildConfig.APPLICATION_ID, clientID = BuildConfig.CLIENT_ID)

        introViewpager.apply {
            adapter = IntroPagerAdapter(supportFragmentManager, lifecycle)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val showConnectButton = position == 2
                    nextButton.isInvisible = showConnectButton
                    connectButton.isInvisible = !showConnectButton
                    signInButton.isInvisible = !showConnectButton
                }
            })
        }

        dotsIndicator.setViewPager2(introViewpager)

        nextButton.setOnClickListener { introViewpager.currentItem = introViewpager.currentItem + 1 }

        connectButton.apply {
            initProgress(this@LoginActivity)
            setOnClickListener {
                signInButton.isEnabled = false
                showProgress()
                trackAccountEvent("openLoginWebview")
                infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
            }
        }

        signInButton.setOnClickListener {
            trackAccountEvent("openCreationWebview")
            openUrl(ApiRoutes.orderDrive())
        }
    }

    private fun authenticateUser(authCode: String) {
        lifecycleScope.launch {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoInterceptor,
                code = authCode,
                onSuccess = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        when (val user = authenticateUser(this@LoginActivity, it)) {
                            is User -> {
                                application.trackCurrentUserId()
                                trackAccountEvent("loggedIn")
                                launchMainActivity()
                            }
                            is ApiResponse<*> -> withContext(Dispatchers.Main) {
                                if (user.error?.code?.equals("no_drive") == true) {
                                    launchNoDriveActivity()
                                } else {
                                    showError(getString(user.translatedError))
                                }
                            }
                            else -> withContext(Dispatchers.Main) { showError(getString(R.string.anErrorHasOccurred)) }
                        }
                    }
                },
                onError = {
                    val error = when (it) {
                        InfomaniakLogin.ErrorStatus.SERVER -> R.string.serverError
                        InfomaniakLogin.ErrorStatus.CONNECTION -> R.string.connectionError
                        else -> R.string.anErrorHasOccurred
                    }
                    showError(getString(error))
                },
            )
        }
    }

    private fun showError(error: String) {
        showSnackbar(error)
        connectButton?.hideProgress(R.string.connect)
        signInButton.isEnabled = true
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun launchNoDriveActivity() {
        val intent = Intent(this, NoDriveActivity::class.java)
        startActivity(intent)
        connectButton?.hideProgress(R.string.connect)
        signInButton.isEnabled = true
    }

    companion object {
        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {

            AccountUtils.getUserById(apiToken.userId)?.let {
                return getErrorResponse(R.string.errorUserAlreadyPresent)
            } ?: run {
                InfomaniakCore.bearerToken = apiToken.accessToken
                val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoInterceptor)
                if (userProfileResponse.result == ApiResponse.Status.ERROR) {
                    return userProfileResponse
                } else {
                    val user: User? = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = ArrayList()
                    }

                    user?.let {
                        val allDrivesDataResponse = ApiRepository.getAllDrivesData(HttpClient.okHttpClientNoInterceptor)

                        when {
                            allDrivesDataResponse.result == ApiResponse.Status.ERROR -> {
                                return allDrivesDataResponse
                            }
                            allDrivesDataResponse.data?.drives?.main?.isEmpty() == true -> {
                                return ApiResponse<DriveInfo>(
                                    result = ApiResponse.Status.ERROR,
                                    error = ApiError(code = "no_drive")
                                )
                            }
                            else -> {
                                allDrivesDataResponse.data?.let { driveInfo ->
                                    DriveInfosController.storeDriveInfos(user.id, driveInfo)
                                    CloudStorageProvider.notifyRootsChanged(context)

                                    AccountUtils.addUser(user)
                                    return user
                                } ?: run {
                                    return getErrorResponse(R.string.serverError)
                                }
                            }
                        }
                    } ?: run {
                        return getErrorResponse(R.string.anErrorHasOccurred)
                    }
                }
            }
        }

        private fun getErrorResponse(@StringRes text: Int): ApiResponse<Any> {
            return ApiResponse(result = ApiResponse.Status.ERROR, translatedError = text)
        }
    }
}
