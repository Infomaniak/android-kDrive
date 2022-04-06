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
package com.infomaniak.drive.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.MODE_PRIVATE
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.requestPermissionsIsPossible
import com.infomaniak.lib.core.utils.startAppSettingsConfig
import io.sentry.Sentry
import io.sentry.SentryLevel

class DrivePermissions {

    companion object {
        private const val SHARED_PREFS = "HINT_BATTERY_OPTIMIZATIONS"
        private const val FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG = "showBatteryOptimizationDialog"
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private lateinit var batteryPermissionResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var registerForActivityResult: ActivityResultLauncher<Array<String>>
    private lateinit var activity: FragmentActivity

    fun registerPermissions(activity: FragmentActivity, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        this.activity = activity
        registerForActivityResult = activity.registerForActivityResult(RequestMultiplePermissions()) { permissions ->
            val authorized = permissions.values.all { it == true }
            resultPermissions(onPermissionResult, authorized)
        }
    }

    fun registerPermissions(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        activity = fragment.requireActivity()
        registerForActivityResult =
            fragment.registerForActivityResult(RequestMultiplePermissions()) { permissions ->
                val authorized = permissions.values.all { it == true }
                resultPermissions(onPermissionResult, authorized)
            }
    }

    fun registerBatteryPermission(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)) {
        activity = fragment.requireActivity()
        batteryPermissionResultLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onPermissionResult(it.resultCode == RESULT_OK)
        }
    }

    private fun resultPermissions(onPermissionResult: ((authorized: Boolean) -> Unit)?, authorized: Boolean) {
        onPermissionResult?.invoke(authorized)
        if (!authorized && !activity.requestPermissionsIsPossible(permissions)) {
            MaterialAlertDialogBuilder(activity, R.style.DialogStyle)
                .setTitle(R.string.androidPermissionTitle)
                .setMessage(R.string.allPermissionNeeded)
                .setCancelable(false)
                .setPositiveButton(R.string.buttonAuthorize) { _: DialogInterface?, _: Int -> activity.startAppSettingsConfig() }
                .show()
        }
    }

    /**
     * Check if the sync has all permissions to work
     * @return [Boolean] true if the sync has all permissions or false
     */
    fun checkSyncPermissions(requestPermission: Boolean = true): Boolean {
        if (mustShowBatteryOptimizationDialog(activity) || !activity.batteryLifePermission(false)) {
            BackgroundSyncPermissionsBottomSheetDialog().show(activity.supportFragmentManager, "syncPermissionsDialog")
        }
        return checkWriteStoragePermission(requestPermission)
    }

    /**
     * Checks if the user has already confirmed write permission
     */
    @SuppressLint("NewApi")
    fun checkWriteStoragePermission(requestPermission: Boolean = true): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
            activity.hasPermissions(permissions) -> true
            else -> {
                if (requestPermission) registerForActivityResult.launch(permissions)
                false
            }
        }
    }

    /**
     * Checks if the user has already confirmed battery optimization's disabling permission
     */
    fun checkBatteryLifePermission(requestPermission: Boolean = true) = activity.batteryLifePermission(requestPermission)

    @SuppressLint("BatteryLife")
    private fun Context.batteryLifePermission(requestPermission: Boolean): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
            powerManager?.isIgnoringBatteryOptimizations(packageName) != false -> true
            else -> {
                if (requestPermission) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )

                    try {
                        batteryPermissionResultLauncher.launch(intent)
                    } catch (activityNotFoundException: ActivityNotFoundException) {
                        try {
                            batteryPermissionResultLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (exception: Exception) {
                            Sentry.withScope { scope ->
                                scope.level = SentryLevel.WARNING
                                Sentry.captureException(exception)
                            }
                        }
                    }
                }
                false
            }
        }
    }

    private fun mustShowBatteryOptimizationDialog(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
        return sharedPrefs.getBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, true)
    }

    fun updateShowBatteryOptimizationDialog(mustShowBatteryDialog: Boolean) {
        val editableSharedPrefs = activity.getSharedPreferences(SHARED_PREFS, MODE_PRIVATE).edit()
        editableSharedPrefs.putBoolean(FLAG_SHOW_BATTERY_OPTIMIZATION_DIALOG, mustShowBatteryDialog).apply()
    }
}
