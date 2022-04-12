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

package com.infomaniak.drive.ui.bottomSheetDialogs

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_bottom_sheet_background_sync.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_categories_information.actionButton

class BackgroundSyncPermissionsBottomSheetDialog(private val drivePermissions: DrivePermissions) : BottomSheetDialogFragment() {

    private val backgroundSyncPermissionsViewModel: BackgroundSyncPermissionsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_background_sync, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drivePermissions.registerBatteryPermission(this) { hasPermission -> onPermissionGranted(hasPermission) }
        activity?.updateShowBatteryOptimizationDialog(true)
        shouldBeWhiteListed(checkWhitelisted(false))

        allowBackgroundSyncSwitch.setOnCheckedChangeListener { _, isChecked -> shouldBeWhiteListed(isChecked) }
        hasDoneNecessaryCheckbox.setOnCheckedChangeListener { _, isChecked ->
            backgroundSyncPermissionsViewModel.hasDoneNecessary = isChecked
        }
        actionButton.setOnClickListener { dismiss() }
        openGuideButton.setOnClickListener { context?.openUrl(SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL) }
    }

    override fun onDismiss(dialog: DialogInterface) {
        with(backgroundSyncPermissionsViewModel) {
            val mustShowDialog = !(hasDoneNecessary && isWhitelisted)
            activity?.updateShowBatteryOptimizationDialog(mustShowDialog)
        }
        super.onDismiss(dialog)
    }

    private fun onPermissionGranted(hasPermission: Boolean) {
        shouldBeWhiteListed(hasPermission)
        showManufacturerWarning(hasPermission)
        backgroundSyncPermissionsViewModel.apply { if (hasPermission && !manufacturerWarning) hasDoneNecessary = true }
    }

    private fun checkWhitelisted(requestPermission: Boolean): Boolean {
        return drivePermissions.checkBatteryLifePermission(requestPermission).also { hasPermission ->
            showManufacturerWarning(hasPermission)
        }
    }

    private fun showManufacturerWarning(hasBatteryPermission: Boolean) {
        with(backgroundSyncPermissionsViewModel) {
            isWhitelisted = hasBatteryPermission
            guideCard.visibility = if (hasBatteryPermission && manufacturerWarning) View.VISIBLE else View.GONE
        }
    }

    private fun shouldBeWhiteListed(shouldBeWhitelisted: Boolean) {
        allowBackgroundSyncSwitch.apply {
            isChecked = shouldBeWhitelisted
            isClickable = !shouldBeWhitelisted
        }
        if (shouldBeWhitelisted && !backgroundSyncPermissionsViewModel.isWhitelisted) checkWhitelisted(true)
    }

    companion object {

        private const val FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG = "SHOW_BATTERY_AUTHORIZATION_DIALOG"
        private const val SHARED_PREFS = "HINT_BATTERY_OPTIMIZATIONS"
        private const val SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL = "https://faq.infomaniak.com/2685"

        fun Context.mustShowBatteryOptimizationDialog(): Boolean {
            val sharedPrefs = getSharedPreferences(SHARED_PREFS, Activity.MODE_PRIVATE)
            return sharedPrefs.getBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, true)
        }

        fun Context.updateShowBatteryOptimizationDialog(mustShowBatteryDialog: Boolean) {
            val editableSharedPrefs = getSharedPreferences(SHARED_PREFS, Activity.MODE_PRIVATE).edit()
            editableSharedPrefs.putBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, mustShowBatteryDialog).apply()
        }
    }
}
