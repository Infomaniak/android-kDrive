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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.arrayMapOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackMyKSuiteEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentSettingsBinding
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.MyKSuiteDataUtils
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.getDashboardData
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.core.utils.openAppNotificationSettings
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@SettingsFragment) { authorized -> if (authorized) requireActivity().syncImmediately() }
        }

        onlyWifiSyncValue.isChecked = AppSettings.onlyWifiSync
        onlyWifiSyncValue.setOnCheckedChangeListener { _, isChecked ->
            trackSettingsEvent("onlyWifiTransfer", isChecked)
            AppSettings.onlyWifiSync = isChecked
            requireActivity().launchAllUpload(drivePermissions)
        }

        setupMyKSuiteLayout()

        syncPicture.setOnClickListener {
            safelyNavigate(R.id.syncSettingsActivity)
        }
        themeSettings.setOnClickListener {
            openThemeSettings()
        }
        notifications.setOnClickListener {
            requireContext().openAppNotificationSettings()
        }
        appSecurity.apply {
            if (LockActivity.hasBiometrics()) {
                appSecuritySeparator.isVisible = true
                isVisible = true
                setOnClickListener {
                    trackSettingsEvent("lockApp")
                    safeNavigate(R.id.appSecurityActivity)
                }
            } else {
                appSecuritySeparator.isGone = true
                isGone = true
            }
        }
        about.setOnClickListener {
            safeNavigate(R.id.aboutSettingsFragment)
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
                myKSuiteSettingsTitle.setText(if (drive.isFreeTier) R.string.myKSuiteName else R.string.myKSuitePlusName)
            }

            dashboardSettings.setOnClickListener {
                trackMyKSuiteEvent(MatomoMyKSuite.OPEN_DASHBOARD_NAME)
                openMyKSuiteDashboard(myKSuiteData)
            }
        }
    }

    private fun openMyKSuiteDashboard(myKSuiteData: MyKSuiteData) {
        val data = getDashboardData(myKSuiteData = myKSuiteData)
        safeNavigate(directions = SettingsFragmentDirections.actionSettingsFragmentToMyKSuiteDashboardFragment(data))
    }

    private fun openThemeSettings() {
        val items = arrayOf(
            getString(R.string.themeSettingsLightLabel),
            getString(R.string.themeSettingsDarkLabel),
            getString(R.string.themeSettingsSystemDefaultLabel)
        )
        val nightMode = arrayMapOf(
            Pair(0, AppCompatDelegate.MODE_NIGHT_NO),
            Pair(1, AppCompatDelegate.MODE_NIGHT_YES),
            Pair(2, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
                trackSettingsEvent("theme${binding.themeSettingsValue.text}")
            }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false).show()
    }

    override fun onResume() = with(binding) {
        super.onResume()
        syncPictureValue.setText(if (AccountUtils.isEnableAppSync()) R.string.allActivated else R.string.allDisabled)
        appSecurityValue.setText(if (AppSettings.appSecurityLock) R.string.allActivated else R.string.allDisabled)
        setThemeSettingsValue()
    }

    private fun setThemeSettingsValue() {
        val themeTextValue = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.string.themeSettingsLightLabel
            AppCompatDelegate.MODE_NIGHT_YES -> R.string.themeSettingsDarkLabel
            else -> R.string.themeSettingsSystemLabel
        }
        binding.themeSettingsValue.setText(themeTextValue)
    }

    private fun trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent("settings", name, value = value?.toFloat())
    }
}
