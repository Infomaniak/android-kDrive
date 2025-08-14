/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.Xor
import com.infomaniak.core.crossapplogin.back.CrossAppLogin
import com.infomaniak.core.crossapplogin.back.DerivedTokenGenerator
import com.infomaniak.core.crossapplogin.back.DerivedTokenGenerator.Issue
import com.infomaniak.core.crossapplogin.back.DerivedTokenGeneratorImpl
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.loginUrl
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.login.ApiToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class CrossAppLoginViewModel() : ViewModel() {

    private val _availableAccounts = MutableStateFlow(emptyList<ExternalAccount>())
    val availableAccounts: StateFlow<List<ExternalAccount>> = _availableAccounts.asStateFlow()
    val skippedAccountIds = MutableStateFlow(emptySet<Long>())

    val selectedAccounts: StateFlow<List<ExternalAccount>> =
        combine(availableAccounts, skippedAccountIds) { allExternalAccounts, idsToSkip ->
            allExternalAccounts.filter { it.id !in idsToSkip }
        }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val derivedTokenGenerator: DerivedTokenGenerator = DerivedTokenGeneratorImpl(
        coroutineScope = viewModelScope,
        tokenRetrievalUrl = "${loginUrl}token",
        hostAppPackageName = BuildConfig.APPLICATION_ID,
        clientId = BuildConfig.CLIENT_ID,
        userAgent = HttpUtils.getUserAgent,
    )

    suspend fun activateUpdates(hostActivity: ComponentActivity): Nothing {
        val crossAppLogin = CrossAppLogin.forContext(
            context = hostActivity,
            coroutineScope = hostActivity.lifecycleScope + Dispatchers.Default
        )
        hostActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
            _availableAccounts.emit(crossAppLogin.retrieveAccountsFromOtherApps())
        }
        awaitCancellation() // Should never be reached. Unfortunately, `repeatOnLifecycle` doesn't return `Nothing`.
    }

    suspend fun attemptLogin(selectedAccounts: List<ExternalAccount>): LoginResult {
        val tokenGenerator = derivedTokenGenerator

        if (selectedAccounts.isEmpty()) return LoginResult.NoSelectedAccount

        val tokens = mutableListOf<ApiToken>()
        val errorIds = mutableListOf<Int>()

        selectedAccounts.forEach { account ->
            when (val result = tokenGenerator.attemptDerivingOneOfTheseTokens(account.tokens)) {
                is Xor.First -> {
                    SentryLog.i(TAG, "Succeeded to derive token for account: ${account.id}")
                    tokens.add(result.value)
                }
                is Xor.Second -> {
                    errorIds.add(getTokenDerivationIssueErrorMessage(account, issue = result.value))
                }
            }
        }

        return when {
            tokens.isEmpty() -> LoginResult.Failure(errorIds)
            errorIds.isEmpty() -> LoginResult.Success(tokens)
            else -> LoginResult.Partial(tokens, errorIds)
        }
    }

    @StringRes
    private fun getTokenDerivationIssueErrorMessage(account: ExternalAccount, issue: Issue): Int {
        val shouldReport: Boolean
        val errorId = when (issue) {
            is Issue.AppIntegrityCheckFailed -> {
                shouldReport = false
                R.string.anErrorHasOccurred
            }
            is Issue.ErrorResponse -> {
                shouldReport = issue.httpStatusCode !in 500..599
                R.string.anErrorHasOccurred
            }
            is Issue.NetworkIssue -> {
                shouldReport = false
                R.string.connectionError
            }
            is Issue.OtherIssue -> {
                shouldReport = true
                R.string.anErrorHasOccurred
            }
        }
        val errorMessage = "Failed to derive token for account ${account.id}, with reason: $issue"
        when (shouldReport) {
            true -> SentryLog.e(TAG, errorMessage)
            false -> SentryLog.i(TAG, errorMessage)
        }

        return errorId
    }

    sealed interface LoginResult {
        data object NoSelectedAccount : LoginResult
        data class Success(val tokens: List<ApiToken>) : LoginResult
        data class Partial(val tokens: List<ApiToken>, val errorMessageIds: List<Int>) : LoginResult
        data class Failure(val errorMessageIds: List<Int>) : LoginResult
    }

    companion object {
        private const val TAG = "CrossAppLoginViewModel"
    }
}
