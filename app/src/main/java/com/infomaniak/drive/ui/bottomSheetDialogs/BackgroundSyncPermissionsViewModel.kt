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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.BuildConfig
import kotlinx.android.synthetic.main.fragment_bottom_sheet_background_sync.*

class BackgroundSyncPermissionsViewModel : ViewModel() {

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

    companion object {

        private const val SHARED_PREFS = "HINT_BATTERY_OPTIMIZATIONS"
        private const val FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG = "showBatteryOptimizationDialog"

        /**
         * List of manufacturers which are known to restrict background processes or otherwise
         * block synchronization.
         *
         * See https://developers.google.com/zero-touch/resources/manufacturer-names for manufacturer values.
         */
        private val evilManufacturers =
            arrayOf("asus", "huawei", "lenovo", "meizu", "oneplus", "oppo", "samsung", "vivo", "xiaomi")

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
}