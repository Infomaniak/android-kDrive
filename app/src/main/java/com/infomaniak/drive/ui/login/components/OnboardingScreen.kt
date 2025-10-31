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
package com.infomaniak.drive.ui.login.components

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.infomaniak.core.compose.basics.ButtonType
import com.infomaniak.core.compose.basics.Typography
import com.infomaniak.core.crossapplogin.back.BaseCrossAppLoginViewModel.Companion.filterSelectedAccounts
import com.infomaniak.core.crossapplogin.back.ExternalAccount
import com.infomaniak.core.crossapplogin.front.components.CrossLoginBottomContent
import com.infomaniak.core.crossapplogin.front.data.CrossLoginDefaults
import com.infomaniak.core.crossapplogin.front.previews.AccountsPreviewParameter
import com.infomaniak.core.onboarding.OnboardingPage
import com.infomaniak.core.onboarding.OnboardingScaffold
import com.infomaniak.core.onboarding.components.OnboardingComponents
import com.infomaniak.core.onboarding.components.OnboardingComponents.DefaultBackground
import com.infomaniak.core.onboarding.components.OnboardingComponents.DefaultLottieIllustration
import com.infomaniak.core.onboarding.components.OnboardingComponents.DefaultTitleAndDescription
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.theme.DriveTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    accounts: () -> List<ExternalAccount>,
    skippedIds: () -> Set<Long>,
    isLoginButtonLoading: () -> Boolean,
    isSignUpButtonLoading: () -> Boolean,
    onLoginRequest: (accounts: List<ExternalAccount>) -> Unit,
    onCreateAccount: () -> Unit,
    onSaveSkippedAccounts: (Set<Long>) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Page.entries.size })

    OnboardingScaffold(
        pagerState = pagerState,
        onboardingPages = Page.entries.mapIndexed { index, page -> page.toOnboardingPage(pagerState, index) },
        bottomContent = { paddingValues ->
            OnboardingComponents.CrossLoginBottomContent(
                modifier = Modifier
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues),
                pagerState = pagerState,
                accounts = accounts,
                skippedIds = skippedIds,
                isLoginButtonLoading = isLoginButtonLoading,
                isSignUpButtonLoading = isSignUpButtonLoading,
                onLogin = { onLoginRequest(emptyList()) },
                onContinueWithSelectedAccounts = { onLoginRequest(accounts().filterSelectedAccounts(skippedIds())) },
                onCreateAccount = onCreateAccount,
                onUseAnotherAccountClicked = { onLoginRequest(emptyList()) },
                onSaveSkippedAccounts = onSaveSkippedAccounts,
                nextButtonShape = CircleShape,
                customization = CrossLoginDefaults.customize(
                    buttonStyle = ButtonType.Drive,
                    colors = CrossLoginDefaults.colors(
                        titleColor = colorResource(R.color.title),
                        descriptionColor = colorResource(R.color.primaryText),
                    ),
                ),
            )
        },
    )
}

@Composable
private fun Page.toOnboardingPage(pagerState: PagerState, index: Int): OnboardingPage = OnboardingPage(
    background = {
        DefaultBackground(ImageVector.vectorResource(backgroundRes), modifier = Modifier.padding(bottom = 300.dp))
    },
    illustration = {
        DefaultLottieIllustration(
            lottieRawRes = illustrationRes,
            isCurrentPageVisible = { pagerState.currentPage == index },
            // Height of the biggest of the three illustrations. Because all animations don't have the same height, we need to
            // force them to have the same height so the content of every page is correctly aligned
            modifier = Modifier.height(270.dp)
        )
    },
    text = {
        DefaultTitleAndDescription(
            title = stringResource(titleRes),
            description = stringResource(descriptionRes),
            titleStyle = Typography.h2.copy(color = colorResource(R.color.title)),
            descriptionStyle = Typography.bodyRegular.copy(color = colorResource(R.color.primaryText)),
        )
    }
)

private enum class Page(
    @DrawableRes val backgroundRes: Int,
    @RawRes val illustrationRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    AccessFiles(
        backgroundRes = R.drawable.ic_back_wave_1,
        illustrationRes = R.raw.illu_devices,
        titleRes = R.string.onBoardingTitle1,
        descriptionRes = R.string.onBoardingDescription1,
    ),
    WorkTogether(
        backgroundRes = R.drawable.ic_back_wave_2,
        illustrationRes = R.raw.illu_collab,
        titleRes = R.string.onBoardingTitle2,
        descriptionRes = R.string.onBoardingDescription2,
    ),
    SaveMemories(
        backgroundRes = R.drawable.ic_back_wave_3,
        illustrationRes = R.raw.illu_photos,
        titleRes = R.string.onBoardingTitle3,
        descriptionRes = R.string.onBoardingDescription3,
    ),
}

@Preview
@Composable
private fun Preview(@PreviewParameter(AccountsPreviewParameter::class) accounts: List<ExternalAccount>) {
    DriveTheme {
        Surface {
            OnboardingScreen(
                accounts = { accounts },
                skippedIds = { emptySet() },
                onLoginRequest = {},
                onCreateAccount = {},
                onSaveSkippedAccounts = {},
                isLoginButtonLoading = { false },
                isSignUpButtonLoading = { false },
            )
        }
    }
}
