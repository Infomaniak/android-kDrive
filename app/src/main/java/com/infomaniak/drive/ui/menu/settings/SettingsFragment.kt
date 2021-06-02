/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.isKeyguardSecure
import com.infomaniak.drive.utils.safeNavigate
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

        val drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { autorized -> if (autorized) requireActivity().syncImmediately() }
        onlyWifiSyncValue.isChecked = AppSettings.onlyWifiSync
        onlyWifiSyncValue.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.onlyWifiSync = isChecked
            requireActivity().launchAllUpload(drivePermissions)
        }

        syncPicture.setOnClickListener {
            safeNavigate(R.id.syncSettingsActivity)
        }
        appSecurity.apply {
            if (requireContext().isKeyguardSecure()) {
                appSecuritySeparator.visibility = VISIBLE
                visibility = VISIBLE
                setOnClickListener {
                    safeNavigate(R.id.appSecurityActivity, null, null)
                }
            } else {
                appSecuritySeparator.visibility = GONE
                visibility = GONE
            }
        }
        notifications.setOnClickListener {
            openAppNotificationSettings()
        }
        about.setOnClickListener {
            safeNavigate(R.id.aboutSettingsFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        syncPictureValue.setText(if (AccountUtils.isEnableAppSync()) R.string.allActivated else R.string.allDisabled)
        appSecurityValue.setText(if (AppSettings.appSecurityLock) R.string.allActivated else R.string.allDisabled)
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
}