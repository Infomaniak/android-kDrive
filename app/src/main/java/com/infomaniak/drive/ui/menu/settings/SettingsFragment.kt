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
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.ksuite.myksuite.ui.utils.MatomoMyKSuite
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
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.MyKSuiteDataUtils
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.getDashboardData
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.BuildConfig.AUTOLOG_URL
import com.infomaniak.lib.core.BuildConfig.TERMINATE_ACCOUNT_URL
import com.infomaniak.lib.core.ui.WebViewActivity
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.openAppNotificationSettings
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

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

        val syncPermissions = DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).apply {
            registerPermissions(this@SettingsFragment) { authorized -> if (authorized) requireActivity().syncImmediately() }
        }

        onlyWifiSync.isChecked = AppSettings.onlyWifiSync
        onlyWifiSync.setOnCheckedChangeListener { _, isChecked ->
            trackSettingsEvent(MatomoName.OnlyWifiTransfer, isChecked)
            AppSettings.onlyWifiSync = isChecked
            requireActivity().launchAllUpload(syncPermissions)
        }

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
        about.setOnClickListener { safelyNavigate(R.id.aboutSettingsFragment) }
        feedback.setOnClickListener { navigateToFeedback() }
        setDeleteAccountClickListener()
        binding.root.enableEdgeToEdge()
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
                trackMyKSuiteEvent(MatomoMyKSuite.OPEN_DASHBOARD_NAME)
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
        if (AccountUtils.currentUser?.isStaff == true) {
            Intent(requireContext(), BugTrackerActivity::class.java).apply {
                putExtras(
                    BugTrackerActivityArgs(
                        user = AccountUtils.currentUser!!,
                        appBuildNumber = BuildConfig.VERSION_NAME,
                        bucketIdentifier = BuildConfig.BUGTRACKER_DRIVE_BUCKET_ID,
                        projectName = BuildConfig.BUGTRACKER_DRIVE_PROJECT_NAME,
                        repoGitHub = BuildConfig.GITHUB_REPO,
                    ).toBundle(),
                )
            }.also(::startActivity)
        } else {
            trackSettingsEvent(MatomoName.Feedback)
            context?.openUrl(requireContext().getString(R.string.urlUserReportAndroid))
        }
    }

    companion object {
        private const val URL_REDIRECT_SUCCESSFUL_ACCOUNT_DELETION = "login.preprod.dev.infomaniak.ch"
        private const val TERMINATE_ACCOUNT_FULL_URL = "$AUTOLOG_URL/?url=$TERMINATE_ACCOUNT_URL"
    }
}
