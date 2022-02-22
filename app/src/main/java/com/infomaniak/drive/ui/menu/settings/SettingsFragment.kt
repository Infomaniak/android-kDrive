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
package com.infomaniak.drive.ui.menu.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import com.infomaniak.drive.utils.MatomoUtils.trackEventWithBooleanValue
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@SettingsFragment) { authorized -> if (authorized) requireActivity().syncImmediately() }
        }

        onlyWifiSyncValue.isChecked = AppSettings.onlyWifiSync
        onlyWifiSyncValue.setOnCheckedChangeListener { _, isChecked ->
            context?.applicationContext?.trackEventWithBooleanValue("settings", "onlyWifiTransfer", isChecked)
            AppSettings.onlyWifiSync = isChecked
            requireActivity().launchAllUpload(drivePermissions)
        }

        syncPicture.setOnClickListener {
            safeNavigate(R.id.syncSettingsActivity)
        }
        themeSettings.setOnClickListener {
            openThemeSettings()
        }
        notifications.setOnClickListener {
            openAppNotificationSettings()
        }
        appSecurity.apply {
            if (requireContext().isKeyguardSecure()) {
                appSecuritySeparator.isVisible = true
                isVisible = true
                setOnClickListener {
                    trackSettingsEvent("lockApp")
                    safeNavigate(R.id.appSecurityActivity, null, null)
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
                trackSettingsEvent("theme${themeSettingsValue.text}")
                UiSettings(requireContext()).nightMode = defaultNightMode
                AppCompatDelegate.setDefaultNightMode(defaultNightMode)
                setThemeSettingsValue()
            }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false).show()
    }

    override fun onResume() {
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
        themeSettingsValue.setText(themeTextValue)
    }

    private fun openAppNotificationSettings() {
        val context = requireContext()
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
                else -> {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    addCategory(Intent.CATEGORY_DEFAULT)
                    data = Uri.parse("package:" + context.packageName)
                }
            }
        }
        startActivity(intent)
    }

    private fun trackSettingsEvent(trackerName: String) {
        trackEvent("settings", TrackerAction.CLICK, trackerName)
    }
}
