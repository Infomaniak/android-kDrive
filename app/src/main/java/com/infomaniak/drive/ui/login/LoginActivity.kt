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
package com.infomaniak.drive.ui.login

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.core.Xor
import com.infomaniak.core.cancellable
import com.infomaniak.core.crossloginui.data.CrossLoginAccount
import com.infomaniak.core.utils.awaitOneClick
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackAccountEvent
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.databinding.ActivityLoginBinding
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.bottomSheetDialogs.CrossLoginBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.CrossLoginBottomSheetDialog.Companion.ON_ANOTHER_ACCOUNT_CLICKED_KEY
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.PublicShareUtils
import com.infomaniak.drive.utils.getInfomaniakLogin
import com.infomaniak.drive.utils.openSupport
import com.infomaniak.lib.core.auth.TokenAuthenticator.Companion.changeAccessToken
import com.infomaniak.lib.core.models.ApiError
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.hideProgressCatching
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.setMargins
import com.infomaniak.lib.core.utils.showProgressCatching
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import splitties.coroutines.repeatWhileActive
import splitties.experimental.ExperimentalSplittiesApi
import com.infomaniak.core.crossloginui.R as RCrossLogin

class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    private val loginViewModel: LoginViewModel by viewModels()

    private val infomaniakLogin: InfomaniakLogin by lazy { getInfomaniakLogin() }

    private val navigationArgs: LoginActivityArgs? by lazy {
        intent?.extras?.let(LoginActivityArgs::fromBundle)
    }

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
                binding.connectButton.hideProgressCatching(R.string.connect)
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

        enableEdgeToEdge()
        setContentView(root)

        configureViewPager()
        dotsIndicator.attachTo(introViewpager)

        connectButton.apply {
            initProgress(this@LoginActivity)
            setOnClickListener {
                signInButton.isEnabled = false
                showProgressCatching()
                openLoginWebView()
            }
        }

        signInButton.setOnClickListener {
            trackAccountEvent(MatomoName.OpenCreationWebview)
            startAccountCreation()
        }

        onBackPressedDispatcher.addCallback {
            if (introViewpager.currentItem == 0) finish() else introViewpager.currentItem -= 1
        }

        handleNavigationFlags()

        if (SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        binding.footer.onApplyWindowInsetsListener { view, insets ->
            view.setMargins(bottom = insets.bottom)
        }

        observeCrossLoginAccounts()
        observeCrossLoginSelectedIds()
        setCrossLoginClickListener()
        initCrossLogin()
    }

    private fun configureViewPager() = with(binding) {
        introViewpager.apply {
            adapter = IntroPagerAdapter(supportFragmentManager, lifecycle)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val showConnectButton = position == 2
                    nextButton.isGone = showConnectButton
                    connectButton.isVisible = showConnectButton
                    if (showConnectButton) {
                        if (loginViewModel.crossLoginAccounts.value!!.isEmpty()) {
                            crossLoginSelection.isGone = true
                            signInButton.isVisible = true
                        } else {
                            signInButton.isGone = true
                            crossLoginSelection.isVisible = true
                        }
                    } else {
                        crossLoginSelection.isGone = true
                        signInButton.isGone = true
                    }
                }
            })

            nextButton.setOnClickListener { currentItem++ }
        }
    }

    private fun startAccountCreation() {
        infomaniakLogin.startCreateAccountWebView(
            resultLauncher = createAccountResultLauncher,
            createAccountUrl = BuildConfig.CREATE_ACCOUNT_URL,
            successHost = BuildConfig.CREATE_ACCOUNT_SUCCESS_HOST,
            cancelHost = BuildConfig.CREATE_ACCOUNT_CANCEL_HOST,
        )
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

    private fun observeCrossLoginAccounts() {
        loginViewModel.crossLoginAccounts.observe(this) { accounts ->
            SentryLog.i(TAG, "Got ${accounts.count()} accounts from other apps")
            binding.crossLoginSelection.setAccounts(accounts)
        }
    }

    private fun observeCrossLoginSelectedIds() {
        loginViewModel.crossLoginSelectedIds.observe(this) { ids ->
            if (ids.isEmpty()) return@observe

            val count = ids.count()
            SentryLog.i(TAG, "User selected $count accounts")
            binding.crossLoginSelection.setSelectedIds(ids)
            binding.connectButton.text = resources.getQuantityString(RCrossLogin.plurals.buttonContinueWithAccounts, count, count)
        }
    }

    private fun setCrossLoginClickListener() {

        // Open Login webView when coming back from CrossLogin bottomSheet
        supportFragmentManager.setFragmentResultListener(
            /* requestKey = */ ON_ANOTHER_ACCOUNT_CLICKED_KEY,
            /* lifecycleOwner = */ this,
        ) { _, bundle ->
            bundle.getString(ON_ANOTHER_ACCOUNT_CLICKED_KEY)?.let { openLoginWebView() }
        }

        // Open CrossLogin bottomSheet
        binding.crossLoginSelection.setOnClickListener {
            CrossLoginBottomSheetDialog().show(supportFragmentManager, null)
        }
    }

    @OptIn(ExperimentalSplittiesApi::class)
    private fun initCrossLogin() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {

            loginViewModel.initDerivedTokenGenerator(coroutineScope = this)

            val accounts = loginViewModel.getCrossLoginAccounts(context = this@LoginActivity)

            if (accounts.isNotEmpty()) {
                loginViewModel.crossLoginAccounts.value = accounts
                loginViewModel.crossLoginSelectedIds.value = accounts.map { it.id }.toSet()
            }

            repeatWhileActive {
                binding.connectButton.awaitOneClick()
                if (accounts.isEmpty()) {
                    binding.signInButton.isEnabled = false
                    openLoginWebView()
                } else {
                    handleCrossAppLogin()
                }
            }
        }
    }

    private suspend fun handleCrossAppLogin() {

        suspend fun authenticateToken(token: ApiToken, withRedirection: Boolean) {
            authenticateUser(token, infomaniakLogin, withRedirection)
        }

        val accounts = loginViewModel.crossLoginAccounts.value
            ?.filter { loginViewModel.crossLoginSelectedIds.value?.contains(it.id) == true }
            ?: return
        val tokenGenerator = loginViewModel.derivedTokenGenerator ?: return

        var firstAccountToken: ApiToken? = null
        val accountsAndTokens = mutableMapOf<CrossLoginAccount, ApiToken?>()

        accounts.forEach { account ->
            val token = when (val result = tokenGenerator.attemptDerivingOneOfTheseTokens(account.tokens)) {
                is Xor.First -> {
                    SentryLog.i(TAG, "Succeeded to derive token for account: ${account.id}")
                    if (firstAccountToken == null) firstAccountToken = result.value
                    result.value
                }
                is Xor.Second -> {
                    SentryLog.e(TAG, "Failed to derive token for account ${account.id}, with reason: ${result.value}")
                    null
                }
            }
            accountsAndTokens.put(account, token)
        }

        val currentlySelectedInAnAppToken = accountsAndTokens.firstNotNullOfOrNull { (account, token) ->
            if (account.isCurrentlySelectedInAnApp) token else null
        }

        val remainingTokens = accountsAndTokens
            .filterNot { (_, token) -> token == currentlySelectedInAnAppToken }
            .values

        remainingTokens.forEach { token ->
            token?.let { authenticateToken(token = it, withRedirection = false) }
        }

        (currentlySelectedInAnAppToken ?: firstAccountToken)?.let { authenticateToken(token = it, withRedirection = true) }
    }

    private fun openLoginWebView() {
        trackAccountEvent(MatomoName.OpenLoginWebview)
        infomaniakLogin.startWebViewLogin(webViewLoginResultLauncher)
    }

    private suspend fun authenticateUser(
        token: ApiToken,
        infomaniakLogin: InfomaniakLogin,
        withRedirection: Boolean = true,
    ) = Dispatchers.Default {
        when (val returnValue = authenticateUser(this@LoginActivity, token)) {
            is User -> {
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
            is ApiResponse<*> -> Dispatchers.Main {
                if (returnValue.error?.description == ErrorCode.NO_DRIVE) {
                    if (withRedirection) launchNoDriveActivity()
                } else {
                    showError(getString(returnValue.translateError()))
                }
            }
            else -> Dispatchers.Main { showError(getString(R.string.anErrorHasOccurred)) }
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
            when (val returnValue = authenticateUser(this@LoginActivity, apiToken)) {
                is User -> {
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
                is ApiResponse<*> -> Dispatchers.Main {
                    if (returnValue.error?.description == ErrorCode.NO_DRIVE) {
                        launchNoDriveActivity()
                    } else {
                        showError(getString(returnValue.translateError()))
                    }
                }
                else -> Dispatchers.Main { showError(getString(R.string.anErrorHasOccurred)) }
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

    private fun showError(error: String) = with(binding) {
        showSnackbar(error)
        connectButton.hideProgressCatching(R.string.connect)
        signInButton.isEnabled = true
        if (!connectButton.isEnabled) connectButton.isEnabled = true
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).clearStack())
    }

    private fun launchNoDriveActivity() = with(binding) {
        Intent(this@LoginActivity, NoDriveActivity::class.java).apply { startActivity(this) }
        connectButton.hideProgressCatching(R.string.connect)
        signInButton.isEnabled = true
    }

    private fun handleNavigationFlags() {
        when {
            navigationArgs?.isHelpShortcutPressed == true -> openSupport()
            navigationArgs?.shouldLaunchAccountCreation == true -> startAccountCreation()
        }
    }

    companion object {
        private val TAG = LoginActivity::class.java.simpleName

        suspend fun authenticateUser(context: Context, apiToken: ApiToken): Any {

            AccountUtils.getUserById(apiToken.userId)?.let {
                return getErrorResponse(R.string.errorUserAlreadyPresent)
            } ?: run {
                val okhttpClient = HttpClient.okHttpClientNoTokenInterceptor.newBuilder().addInterceptor { chain ->
                    val newRequest = changeAccessToken(chain.request(), apiToken)
                    chain.proceed(newRequest)
                }.build()
                val userProfileResponse = ApiRepository.getUserProfile(okhttpClient)

                if (userProfileResponse.result == ApiResponseStatus.ERROR) {
                    return userProfileResponse
                } else {
                    val user: User? = userProfileResponse.data?.apply {
                        this.apiToken = apiToken
                        this.organizations = ArrayList()
                    }

                    user?.let {
                        val allDrivesDataResponse = Dispatchers.IO { ApiRepository.getAllDrivesData(okhttpClient) }

                        when {
                            allDrivesDataResponse.result == ApiResponseStatus.ERROR -> {
                                return allDrivesDataResponse
                            }
                            allDrivesDataResponse.data?.drives?.any { it.isDriveUser() } == false -> {
                                return ApiResponse<DriveInfo>(
                                    result = ApiResponseStatus.ERROR,
                                    error = ApiError(code = ErrorCode.NO_DRIVE)
                                )
                            }
                            else -> {
                                allDrivesDataResponse.data?.let { driveInfo ->
                                    Dispatchers.IO {
                                        DriveInfosController.storeDriveInfos(user.id, driveInfo)
                                    }
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
