/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.MatomoDrive.trackAccountEvent
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.databinding.ActivityLoginBinding
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.getInfomaniakLogin
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    private val infomaniakLogin: InfomaniakLogin by lazy { getInfomaniakLogin() }

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
                binding.connectButton.hideProgress(R.string.connect)
                binding.signInButton.isEnabled = true
            }
        }
    }

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        lockOrientationForSmallScreens()
        super.onCreate(savedInstanceState)
        setContentView(root)

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

            nextButton.setOnClickListener { currentItem++ }
        }

        dotsIndicator.attachTo(introViewpager)

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
            infomaniakLogin.startCreateAccountWebView(
                resultLauncher = createAccountResultLauncher,
                createAccountUrl = BuildConfig.CREATE_ACCOUNT_URL,
                successHost = BuildConfig.CREATE_ACCOUNT_SUCCESS_HOST,
                cancelHost = BuildConfig.CREATE_ACCOUNT_CANCEL_HOST,
            )
        }

        onBackPressedDispatcher.addCallback {
            if (introViewpager.currentItem == 0) finish() else introViewpager.currentItem -= 1
        }
    }

    private fun ActivityResult.handleCreateAccountActivityResult() = with(binding) {
        if (resultCode == RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher, false)
                else -> showError(translatedError)
            }
        } else {
            connectButton.isEnabled = true
            signInButton.isEnabled = true
        }
    }

    private fun authenticateUser(authCode: String) {
        lifecycleScope.launch {
            infomaniakLogin.getToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                code = authCode,
                onSuccess = ::onGetTokenSuccess,
                onError = { showError(getLoginErrorDescription(this@LoginActivity, it)) },
            )
        }
    }

    private fun onGetTokenSuccess(apiToken: ApiToken) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val returnValue = authenticateUser(this@LoginActivity, apiToken)) {
                is User -> {
                    trackUserId(AccountUtils.currentUserId)
                    trackAccountEvent("loggedIn")
                    launchMainActivity()
                    return@launch
                }
                is ApiResponse<*> -> withContext(Dispatchers.Main) {
                    if (returnValue.error?.code == ErrorCode.NO_DRIVE) {
                        launchNoDriveActivity()
                    } else {
                        showError(getString(returnValue.translatedError))
                    }
                }
                else -> withContext(Dispatchers.Main) { showError(getString(R.string.anErrorHasOccurred)) }
            }

            infomaniakLogin.deleteToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                token = apiToken,
                onError = { SentryLog.e("DeleteTokenError", "API response error: $it") },
            )
        }
    }

    private fun showError(error: String) = with(binding) {
        showSnackbar(error)
        connectButton.hideProgress(R.string.connect)
        signInButton.isEnabled = true
        if (!connectButton.isEnabled) connectButton.isEnabled = true
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun launchNoDriveActivity() = with(binding) {
        Intent(this@LoginActivity, NoDriveActivity::class.java).apply { startActivity(this) }
        connectButton.hideProgress(R.string.connect)
        signInButton.isEnabled = true
    }

    companion object {
        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {

            AccountUtils.getUserById(apiToken.userId)?.let {
                return getErrorResponse(R.string.errorUserAlreadyPresent)
            } ?: run {
                InfomaniakCore.bearerToken = apiToken.accessToken
                val userProfileResponse = ApiRepository.getUserProfile(HttpClient.okHttpClientNoTokenInterceptor)
                if (userProfileResponse.result == ApiResponseStatus.ERROR) {
                    return userProfileResponse
                } else {
                    val user: User? = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = ArrayList()
                    }

                    user?.let {
                        val allDrivesDataResponse = ApiRepository.getAllDrivesData(HttpClient.okHttpClientNoTokenInterceptor)

                        when {
                            allDrivesDataResponse.result == ApiResponseStatus.ERROR -> {
                                return allDrivesDataResponse
                            }
                            allDrivesDataResponse.data?.drives?.main?.isEmpty() == true -> {
                                return ApiResponse<DriveInfo>(
                                    result = ApiResponseStatus.ERROR,
                                    error = ApiError(code = ErrorCode.NO_DRIVE)
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
            return ApiResponse(result = ApiResponseStatus.ERROR, translatedError = text)
        }

        fun getLoginErrorDescription(context: Context, error: InfomaniakLogin.ErrorStatus): String {
            return context.getString(
                when (error) {
                    InfomaniakLogin.ErrorStatus.SERVER -> R.string.serverError
                    InfomaniakLogin.ErrorStatus.CONNECTION -> R.string.connectionError
                    else -> R.string.anErrorHasOccurred
                }
            )
        }
    }
}
