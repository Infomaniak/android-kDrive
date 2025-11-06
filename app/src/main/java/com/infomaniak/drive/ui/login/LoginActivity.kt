/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalSplittiesApi::class)

package com.infomaniak.drive.ui.login

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.Xor
import com.infomaniak.core.cancellable
import com.infomaniak.core.compose.basics.CallableState
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.core.legacy.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.core.legacy.models.ApiError
import com.infomaniak.core.legacy.models.ApiResponse
import com.infomaniak.core.legacy.models.ApiResponseStatus
import com.infomaniak.core.legacy.models.user.User
import com.infomaniak.core.legacy.networking.HttpClient
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.core.observe
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.drive.CREATE_ACCOUNT_CANCEL_HOST
import com.infomaniak.drive.CREATE_ACCOUNT_SUCCESS_HOST
import com.infomaniak.drive.CREATE_ACCOUNT_URL
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackAccountEvent
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.twoFactorAuthManager
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.login.components.OnboardingScreen
import com.infomaniak.drive.ui.theme.DriveTheme
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.PublicShareUtils
import com.infomaniak.drive.utils.getInfomaniakLogin
import com.infomaniak.drive.utils.openSupport
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import splitties.coroutines.repeatWhileActive
import splitties.experimental.ExperimentalSplittiesApi

class LoginActivity : ComponentActivity() {

    private val crossAppLoginViewModel: CrossAppLoginViewModel by viewModels()

    private val infomaniakLogin: InfomaniakLogin by lazy { getInfomaniakLogin() }

    private val navigationArgs: LoginActivityArgs? by lazy {
        intent?.extras?.let(LoginActivityArgs::fromBundle)
    }

    private val loginRequest = CallableState<List<ExternalAccount>>()
    private var isLoginButtonLoading by mutableStateOf(false)
    private var isSignUpButtonLoading by mutableStateOf(false)

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
                isLoginButtonLoading = false
                isSignUpButtonLoading = false
            }
        }
    }

    private val createAccountResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.handleCreateAccountActivityResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationForSmallScreens()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        setContent {
            val accounts by crossAppLoginViewModel.availableAccounts.collectAsStateWithLifecycle()
            val skippedIds by crossAppLoginViewModel.skippedAccountIds.collectAsStateWithLifecycle()

            DriveTheme {
                Surface {
                    OnboardingScreen(
                        accounts = { accounts },
                        skippedIds = { skippedIds },
                        isLoginButtonLoading = { loginRequest.isAwaitingCall.not() || isLoginButtonLoading },
                        isSignUpButtonLoading = { isSignUpButtonLoading },
                        onLoginRequest = { accounts -> loginRequest(accounts) },
                        onCreateAccount = { openAccountCreationWebView() },
                        onSaveSkippedAccounts = { crossAppLoginViewModel.skippedAccountIds.value = it },
                    )
                }
            }
            TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager)
        }

        handleNavigationFlags()

        observeCrossLoginAccounts()
        initCrossLogin()
    }

    private suspend fun handleLogin(loginRequest: CallableState<List<ExternalAccount>>): Nothing = repeatWhileActive {
        val accountsToLogin = loginRequest.awaitOneCall()
        if (accountsToLogin.isEmpty()) openLoginWebView()
        else connectAccounts(selectedAccounts = accountsToLogin)
    }

    private suspend fun connectAccounts(selectedAccounts: List<ExternalAccount>) {
        val loginResult = crossAppLoginViewModel.attemptLogin(selectedAccounts)

        with(loginResult) {
            tokens.forEachIndexed { index, token ->
                authenticateUser(token, infomaniakLogin, withRedirection = index == tokens.lastIndex)
            }

            errorMessageIds.forEach { errorId -> showError(getString(errorId)) }
        }
    }

    private fun ActivityResult.handleCreateAccountActivityResult() {
        if (resultCode == RESULT_OK) {
            val translatedError = data?.getStringExtra(InfomaniakLogin.ERROR_TRANSLATED_TAG)
            when {
                translatedError.isNullOrBlank() -> infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher, false)
                else -> showError(translatedError)
            }
        } else {
            isSignUpButtonLoading = false
        }
    }

    private fun observeCrossLoginAccounts() {
        crossAppLoginViewModel.availableAccounts.observe(this) { accounts ->
            SentryLog.i(TAG, "Got ${accounts.count()} accounts from other apps")
        }
    }

    private fun initCrossLogin() = lifecycleScope.launch {
        launch { crossAppLoginViewModel.activateUpdates(this@LoginActivity) }
        launch { handleLogin(loginRequest) }
    }

    private fun openLoginWebView() {
        isLoginButtonLoading = true
        trackAccountEvent(MatomoName.OpenLoginWebview)
        infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private fun openAccountCreationWebView() {
        isSignUpButtonLoading = true
        trackAccountEvent(MatomoName.OpenCreationWebview)
        startAccountCreation()
    }

    private fun startAccountCreation() {
        infomaniakLogin.startCreateAccountWebView(
            resultLauncher = createAccountResultLauncher,
            createAccountUrl = CREATE_ACCOUNT_URL,
            successHost = CREATE_ACCOUNT_SUCCESS_HOST,
            cancelHost = CREATE_ACCOUNT_CANCEL_HOST,
        )
    }

    private suspend fun authenticateUser(
        token: ApiToken,
        infomaniakLogin: InfomaniakLogin,
        withRedirection: Boolean = true,
    ) = Dispatchers.Default {
        when (val result: Xor<User, ApiResponse<*>> = authenticateUser(this@LoginActivity, token)) {
            is Xor.First -> {
                if (withRedirection) {
                    val deeplink = navigationArgs?.publicShareDeeplink
                    if (deeplink.isNullOrBlank()) {
                        trackUserId(AccountUtils.currentUserId)
                        trackAccountEvent(MatomoName.LoggedIn)
                        launchMainActivity()
                    } else {
                        PublicShareUtils.launchDeeplink(activity = this@LoginActivity, deeplink = deeplink, shouldFinish = true)
                    }
                }

                return@Default
            }
            is Xor.Second -> Dispatchers.Main {
                if (result.value.error?.description == ErrorCode.NO_DRIVE) {
                    if (withRedirection) launchNoDriveActivity()
                } else {
                    showError(getString(result.value.translateError()))
                }
            }
        }

        runCatching {
            infomaniakLogin.deleteToken(
                okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                token = token,
            )?.let { errorStatus ->
                SentryLog.i("DeleteTokenError", "API response error: $errorStatus")
            }
        }.cancellable().onFailure { exception ->
            SentryLog.e(TAG, "Failure on deleteToken", exception)
        }
    }

    private fun authenticateUser(authCode: String) {
        lifecycleScope.launch {
            runCatching {
                val tokenResult = infomaniakLogin.getToken(
                    okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                    code = authCode,
                )

                when (tokenResult) {
                    is InfomaniakLogin.TokenResult.Success -> onGetTokenSuccess(tokenResult.apiToken)
                    is InfomaniakLogin.TokenResult.Error -> {
                        showError(getLoginErrorDescription(this@LoginActivity, tokenResult.errorStatus))
                    }
                }
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
                SentryLog.e(TAG, "Failure on getToken", exception)
            }
        }
    }

    private fun onGetTokenSuccess(apiToken: ApiToken) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result: Xor<User, ApiResponse<*>> = authenticateUser(this@LoginActivity, apiToken)) {
                is Xor.First -> {
                    val deeplink = navigationArgs?.publicShareDeeplink
                    if (deeplink.isNullOrBlank()) {
                        trackUserId(AccountUtils.currentUserId)
                        trackAccountEvent(MatomoName.LoggedIn)
                        launchMainActivity()
                    } else {
                        PublicShareUtils.launchDeeplink(activity = this@LoginActivity, deeplink = deeplink, shouldFinish = true)
                    }

                    return@launch
                }
                is Xor.Second -> Dispatchers.Main {
                    if (result.value.error?.description == ErrorCode.NO_DRIVE) {
                        launchNoDriveActivity()
                    } else {
                        showError(getString(result.value.translateError()))
                    }
                }
            }

            runCatching {
                infomaniakLogin.deleteToken(
                    okHttpClient = HttpClient.okHttpClientNoTokenInterceptor,
                    token = apiToken,
                )?.let { errorStatus ->
                    SentryLog.i("DeleteTokenError", "API response error: $errorStatus")
                }
            }.cancellable().onFailure { exception ->
                SentryLog.e(TAG, "Failure on deleteToken", exception)
            }
        }
    }

    private fun showError(error: String) {
        showSnackbar(error)
        isLoginButtonLoading = false
        isSignUpButtonLoading = false
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun launchNoDriveActivity() {
        Intent(this@LoginActivity, NoDriveActivity::class.java).apply { startActivity(this) }
        isLoginButtonLoading = false
        isSignUpButtonLoading = false
    }

    private fun handleNavigationFlags() {
        when {
            navigationArgs?.isHelpShortcutPressed == true -> openSupport()
            navigationArgs?.shouldLaunchAccountCreation == true -> startAccountCreation()
        }
    }

    companion object {
        private val TAG = LoginActivity::class.java.simpleName

        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Xor<User, ApiResponse<*>> {

            val dbUser = AccountUtils.getUserById(apiToken.userId)
            if (dbUser != null) return Xor.Second(getErrorResponse(R.string.errorUserAlreadyPresent))

            val okhttpClient = HttpClient.okHttpClientNoTokenInterceptor
                .newBuilder()
                .addInterceptor { chain ->
                    val newRequest = changeAccessToken(chain.request(), apiToken)
                    chain.proceed(newRequest)
                }
                .build()
            val userProfileResponse = ApiRepository.getUserProfile(okhttpClient)

            if (userProfileResponse.result == ApiResponseStatus.ERROR) return Xor.Second(userProfileResponse)

            val user = userProfileResponse.data?.apply {
                this.apiToken = apiToken
                this.organizations = ArrayList()
            } ?: return Xor.Second(getErrorResponse(R.string.anErrorHasOccurred))

            val allDrivesDataResponse = Dispatchers.IO { ApiRepository.getAllDrivesData(okhttpClient) }

            return when {
                allDrivesDataResponse.result == ApiResponseStatus.ERROR -> Xor.Second(allDrivesDataResponse)
                allDrivesDataResponse.data?.drives?.any { it.isDriveUser() } == false -> Xor.Second(
                    ApiResponse<DriveInfo>(
                        result = ApiResponseStatus.ERROR,
                        error = ApiError(code = ErrorCode.NO_DRIVE),
                    ),
                )
                else -> {
                    val driveInfo = allDrivesDataResponse.data ?: return Xor.Second(getErrorResponse(R.string.serverError))

                    Dispatchers.IO { DriveInfosController.storeDriveInfos(user.id, driveInfo) }
                    CloudStorageProvider.notifyRootsChanged(context)

                    AccountUtils.addUser(user)
                    Xor.First(user)
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
