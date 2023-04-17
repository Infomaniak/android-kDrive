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
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.lib.core.utils.openAppNotificationSettings
import com.infomaniak.lib.core.utils.safeNavigate
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
            trackSettingsEvent("onlyWifiTransfer", isChecked)
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
            requireContext().openAppNotificationSettings()
        }
        appSecurity.apply {
            if (requireContext().isKeyguardSecure()) {
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
                trackSettingsEvent("theme${themeSettingsValue.text}")
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

    private fun trackSettingsEvent(name: String, value: Boolean? = null) {
        trackEvent("settings", name, value = value?.toFloat())
    }
}
