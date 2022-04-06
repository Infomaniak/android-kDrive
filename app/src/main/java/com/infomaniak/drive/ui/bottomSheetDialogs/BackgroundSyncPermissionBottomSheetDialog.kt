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
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.databinding.FragmentBottomSheetBackgroundSyncBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog.BackgroundSyncPermissionsViewModel.Companion.manufacturerWarning
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog.BackgroundSyncPermissionsViewModel.Companion.updateShowBatteryOptimizationDialog
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.fragment_bottom_sheet_background_sync.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_categories_information.actionButton

class BackgroundSyncPermissionsBottomSheetDialog(private val drivePermissions: DrivePermissions) : BottomSheetDialogFragment() {

    private val backgroundSyncPermissionsViewModel: BackgroundSyncPermissionsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentBottomSheetBackgroundSyncBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.syncPermissionsModel = backgroundSyncPermissionsViewModel

        drivePermissions.registerBatteryPermission(this) { hasPermission -> onPermissionGranted(hasPermission) }
        activity?.updateShowBatteryOptimizationDialog(true)

        backgroundSyncPermissionsViewModel.apply {
            shouldBeWhitelisted.value = checkWhitelisted(false)
            shouldBeWhitelisted.observe(viewLifecycleOwner) { shouldBeWhitelisted ->
                if (shouldBeWhitelisted && isWhitelisted.value != true) checkWhitelisted(true)
            }
            hasDoneNecessary.observe(viewLifecycleOwner) { hasDoneNecessary ->
                val mustShowBatteryDialog = !(hasDoneNecessary && isWhitelisted.value == true)
                activity?.updateShowBatteryOptimizationDialog(mustShowBatteryDialog)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actionButton.setOnClickListener { dismiss() }
        openGuideButton.setOnClickListener { context?.openUrl(BuildConfig.SUPPORT_DONT_KILL_MY_APP_URL) }
    }

    private fun onPermissionGranted(hasPermission: Boolean) {
        backgroundSyncPermissionsViewModel.apply {
            shouldBeWhitelisted.value = hasPermission
            showManufacturerWarning(hasPermission)
            if (hasPermission && !manufacturerWarning) hasDoneNecessary.value = true
        }
    }

    private fun checkWhitelisted(requestPermission: Boolean): Boolean {
        return drivePermissions.checkBatteryLifePermission(requestPermission).also { hasPermission ->
            backgroundSyncPermissionsViewModel.showManufacturerWarning(hasPermission)
        }
    }

    class BackgroundSyncPermissionsViewModel : ViewModel() {

        companion object {

            private const val SHARED_PREFS = "HINT_BATTERY_OPTIMIZATIONS"
            private const val FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG = "showBatteryOptimizationDialog"

            /**
             * List of manufacturers which are known to restrict background processes or otherwise
             * block synchronization.
             *
             * See https://github.com/jaredrummler/AndroidDeviceNames/blob/master/json/ for manufacturer values.
             */
            private val evilManufacturers = arrayOf("huawei", "oneplus", "samsung", "xiaomi")

            /**
             * Whether the device has been produced by an evil manufacturer.
             * Always true for debug builds (to test the UI).
             *
             * @see evilManufacturers
             */
            val manufacturerWarning = evilManufacturers.contains(Build.MANUFACTURER.lowercase()) || BuildConfig.DEBUG

            fun Context.mustShowBatteryOptimizationDialog(): Boolean {
                val sharedPrefs = getSharedPreferences(SHARED_PREFS, Activity.MODE_PRIVATE)
                return sharedPrefs.getBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, true)
            }

            fun Context.updateShowBatteryOptimizationDialog(mustShowBatteryDialog: Boolean) {
                val editableSharedPrefs = getSharedPreferences(SHARED_PREFS, Activity.MODE_PRIVATE).edit()
                editableSharedPrefs.putBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, mustShowBatteryDialog).apply()
            }
        }

        /**
         * As long as it's false, the dialog will be displayed when user use a functionality needing background sync
         *
         * @see hasDoneNecessaryCheckbox
         */
        val hasDoneNecessary = MutableLiveData<Boolean>()

        /**
         * True if the IGNORE_BATTERY_OPTIMIZATIONS permission is given to the app
         */
        val isWhitelisted = MutableLiveData<Boolean>()

        /**
         * Requests battery permission when passed to true
         *
         * @see allowBackgroundSyncSwitch
         */
        val shouldBeWhitelisted = MutableLiveData<Boolean>()

        /**
         * Displays a warning card if true
         *
         * @see guideCard
         */
        val showManufacturerWarning = MutableLiveData<Boolean>()

        fun showManufacturerWarning(hasBatteryPermission: Boolean) {
            isWhitelisted.value = hasBatteryPermission
            showManufacturerWarning.value = hasBatteryPermission && manufacturerWarning
        }
    }
}
