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
package com.infomaniak.drive.ui.menu.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.arrayMapOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.auth.room.UserDatabase
import com.infomaniak.core.crossapplogin.back.CrossAppLogin
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.core.legacy.applock.LockActivity
import com.infomaniak.core.legacy.bugtracker.BugTrackerActivity
import com.infomaniak.core.legacy.bugtracker.BugTrackerActivityArgs
import com.infomaniak.core.legacy.ui.WebViewActivity
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.legacy.utils.openAppNotificationSettings
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.network.AUTOLOG_URL
import com.infomaniak.core.network.ApiEnvironment
import com.infomaniak.core.network.TERMINATE_ACCOUNT_URL
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackMyKSuiteEvent
import com.infomaniak.drive.MatomoDrive.trackSettingsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentSettingsBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MyKSuiteDataUtils
import com.infomaniak.drive.utils.getDashboardData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding by safeBinding()

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val resultActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) AccountUtils.currentUser?.let(settingsViewModel::disconnectDeletedUser)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }


        setupMyKSuiteLayout()

        syncPicture.setOnClickListener { safelyNavigate(R.id.syncSettingsActivity) }
        themeSettings.setOnClickListener { openThemeSettings() }
        notifications.setOnClickListener { requireContext().openAppNotificationSettings() }
        appSecurity.apply {
            if (LockActivity.hasBiometrics()) {
                isVisible = true
                setOnClickListener {
                    trackSettingsEvent(MatomoName.LockApp)
                    safelyNavigate(R.id.appSecurityActivity)
                }
            } else {
                isGone = true
            }
        }

        fileSync.setOnClickListener { safelyNavigate(R.id.syncFilesBottomSheetDialog) }
        registerFileSyncSettingResultListener()
        about.setOnClickListener { safelyNavigate(R.id.aboutSettingsFragment) }
        feedback.setOnClickListener { navigateToFeedback() }
        setDeleteAccountClickListener()
        binding.root.enableEdgeToEdge()

        showCrossAppDeviceIdIfStaff(binding.crossAppDeviceId)
    }

    private fun showCrossAppDeviceIdIfStaff(targetView: ItemSettingView) {
        lifecycleScope.launch {
            UserDatabase().userDao().allUsers.map { list -> list.any { it.isStaff } }.collectLatest { hasStaffAccount ->
                if (!hasStaffAccount) return@collectLatest
                targetView.isVisible = true
                val crossAppLogin = CrossAppLogin.forContext(requireContext(), this)
                @OptIn(ExperimentalUuidApi::class)
                crossAppLogin.sharedDeviceIdFlow.collect { crossAppDeviceId ->
                    targetView.description = crossAppDeviceId.toHexDashString()
                }
            }
        }
    }

    private fun toggleMyKSuiteLayoutVisibility(isVisible: Boolean) {
        binding.myKSuiteSettingsTitle.isVisible = isVisible
        binding.myKSuiteLayout.isVisible = isVisible
    }

    private fun setupMyKSuiteLayout() = with(binding) {
        toggleMyKSuiteLayoutVisibility(MyKSuiteDataUtils.myKSuite != null)

        MyKSuiteDataUtils.myKSuite?.let { myKSuiteData ->

            myKSuiteSettingsEmail.text = myKSuiteData.mail.email

            AccountUtils.getCurrentDrive()?.let { drive ->
                myKSuiteSettingsTitle.setText(if (drive.isKSuitePersoFree) R.string.myKSuiteName else R.string.myKSuitePlusName)
            }

            dashboardSettings.setOnClickListener {
                trackMyKSuiteEvent(MatomoKSuite.OPEN_DASHBOARD_NAME)
                openMyKSuiteDashboard(myKSuiteData)
            }
        }
    }

    private fun setDeleteAccountClickListener() = with(binding) {
        deleteMyAccount.setOnClickListener {
            WebViewActivity.startActivity(
                context = requireContext(),
                url = TERMINATE_ACCOUNT_FULL_URL,
                headers = mapOf("Authorization" to "Bearer ${AccountUtils.currentUser?.apiToken?.accessToken}"),
                urlToQuit = URL_REDIRECT_SUCCESSFUL_ACCOUNT_DELETION,
                activityResultLauncher = resultActivityResultLauncher,
            )
        }
    }

    private fun openMyKSuiteDashboard(myKSuiteData: MyKSuiteData) {
        val user = AccountUtils.currentUser ?: return
        val data = getDashboardData(myKSuiteData, user)
        safeNavigate(directions = SettingsFragmentDirections.actionSettingsFragmentToMyKSuiteDashboardFragment(data))
    }

    private fun openThemeSettings() {
        val items = arrayOf(
            getString(R.string.themeSettingsLightLabel),
            getString(R.string.themeSettingsDarkLabel),
            getString(R.string.themeSettingsSystemDefaultLabel),
        )
        val nightMode = arrayMapOf(
            Pair(0, AppCompatDelegate.MODE_NIGHT_NO),
            Pair(1, AppCompatDelegate.MODE_NIGHT_YES),
            Pair(2, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        )
        var defaultNightMode = AppCompatDelegate.getDefaultNightMode()
        val startSelectItemPosition = nightMode.filter { it.value == defaultNightMode }.keys.first()
        MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(getString(R.string.syncSettingsButtonSaveDate))
            .setSingleChoiceItems(items, startSelectItemPosition) { _, which ->
                defaultNightMode = nightMode[which]!!
            }
            .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                UiSettings(requireContext()).nightMode = defaultNightMode
                AppCompatDelegate.setDefaultNightMode(defaultNightMode)
                setThemeSettingsValue()
                trackSettingsEvent("theme${binding.themeSettings.endText}")
            }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false).show()
    }

    override fun onResume() = with(binding) {
        super.onResume()
        syncPicture.endText = getString(if (AccountUtils.isEnableAppSync()) R.string.allActivated else R.string.allDisabled)
        appSecurity.endText = getString(if (AppSettings.appSecurityLock) R.string.allActivated else R.string.allDisabled)
        setThemeSettingsValue()
    }

    private fun setThemeSettingsValue() {
        val themeTextValue = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.string.themeSettingsLightLabel
            AppCompatDelegate.MODE_NIGHT_YES -> R.string.themeSettingsDarkLabel
            else -> R.string.themeSettingsSystemLabel
        }
        binding.themeSettings.endText = getString(themeTextValue)
    }

    private fun navigateToFeedback() {
        val user = AccountUtils.currentUser
        if (user?.isStaff == true) {
            Intent(requireContext(), BugTrackerActivity::class.java).apply {
                putExtras(
                    BugTrackerActivityArgs(
                        userId = user.id,
                        userCurrentOrganizationId = user.preferences.organizationPreference.currentOrganizationId,
                        userEmail = user.email,
                        userDisplayName = user.displayName,
                        appId = BuildConfig.APPLICATION_ID,
                        appBuildNumber = BuildConfig.VERSION_NAME,
                        bucketIdentifier = BuildConfig.BUGTRACKER_DRIVE_BUCKET_ID,
                        projectName = BuildConfig.BUGTRACKER_DRIVE_PROJECT_NAME,
                    ).toBundle(),
                )
            }.also(::startActivity)
        } else {
            trackSettingsEvent(MatomoName.Feedback)
            context?.openUrl(requireContext().getString(R.string.urlUserReportAndroid))
        }
    }

    private fun registerFileSyncSettingResultListener() {
        getBackNavigationResult<Boolean>(KEY_BACK_ACTION_BOTTOM_SHEET) { isOnlyWifiSyncOffline ->
            val (title, description) =
                if (isOnlyWifiSyncOffline) R.string.syncOnlyWifiTitle to R.string.syncOnlyWifiDescription
                else R.string.syncWifiAndMobileDataTitle to R.string.syncWifiAndMobileDataDescription
            binding.fileSync.title = getString(title)
            binding.fileSync.description = getString(description)
        }
    }

    companion object {
        private val URL_REDIRECT_SUCCESSFUL_ACCOUNT_DELETION = "login.${ApiEnvironment.current.host}"
        private val TERMINATE_ACCOUNT_FULL_URL = "$AUTOLOG_URL/?url=$TERMINATE_ACCOUNT_URL"
        const val KEY_BACK_ACTION_BOTTOM_SHEET = "syncFilesBottomSheetDialog"

        enum class SyncFilesOption(@StringRes val title: Int) {
            ONLY_WIFI(title = R.string.syncOnlyWifiTitle), ALL_DATA(title = R.string.syncWifiAndMobileDataTitle),
        }
    }
}
