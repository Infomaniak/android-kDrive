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
import android.app.Activity
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
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.requestPermissionsIsPossible
import com.infomaniak.lib.core.utils.startAppSettingsConfig
import io.sentry.Sentry
import io.sentry.SentryLevel

class DrivePermissions {

    companion object {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            else -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        fun Activity.resultPermissions(authorized: Boolean, permissions: Array<String>, message: Int) {
            if (!authorized && !requestPermissionsIsPossible(permissions)) {
                MaterialAlertDialogBuilder(this, R.style.DialogStyle)
                    .setTitle(R.string.androidPermissionTitle)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.buttonAuthorize) { _: DialogInterface?, _: Int -> startAppSettingsConfig() }
                    .show()
            }
        }
    }

    private lateinit var batteryPermissionResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var registerForActivityResult: ActivityResultLauncher<Array<String>>
    private lateinit var activity: FragmentActivity

    private val backgroundSyncPermissionsBottomSheetDialog by lazy { BackgroundSyncPermissionsBottomSheetDialog() }

    private val permissionNeeded = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> R.string.allPermissionNeededAndroid13
        else -> R.string.allPermissionNeeded
    }

    fun registerPermissions(activity: FragmentActivity, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        this.activity = activity
        registerForActivityResult = activity.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
            val authorized = authorizedPermissions.values.all { it }
            onPermissionResult?.invoke(authorized)
            activity.resultPermissions(authorized, permissions, permissionNeeded)
        }
    }

    fun registerPermissions(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        activity = fragment.requireActivity()
        registerForActivityResult =
            fragment.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
                val authorized = authorizedPermissions.values.all { it }
                onPermissionResult?.invoke(authorized)
                activity.resultPermissions(authorized, permissions, permissionNeeded)
            }
    }

    fun registerBatteryPermission(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)) {
        activity = fragment.requireActivity()
        batteryPermissionResultLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // TODO remove this fix when api 32 will be stable
            // Fix to bypass the api 32 permission intent returning result_cancelled for both deny and allow action
            val isAboveApi31 = Build.VERSION.SDK_INT > Build.VERSION_CODES.S
            val hasPermission = it.resultCode == RESULT_OK || (isAboveApi31 && checkBatteryLifePermission(false))
            onPermissionResult(hasPermission)
        }
    }

    /**
     * Check if the sync has all permissions to work
     * @return [Boolean] true if the sync has all permissions or false
     */
    fun checkSyncPermissions(requestPermission: Boolean = true): Boolean {

        fun displayBatteryDialog() {
            with(backgroundSyncPermissionsBottomSheetDialog) {
                if (dialog?.isShowing != true && !isResumed) {
                    show(this@DrivePermissions.activity.supportFragmentManager, "syncPermissionsDialog")
                }
            }
        }

        if (UiSettings(activity).mustDisplayBatteryDialog || !checkBatteryLifePermission(false)) {
            displayBatteryDialog()
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
    fun checkBatteryLifePermission(requestPermission: Boolean): Boolean {
        return with(activity) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
                powerManager?.isIgnoringBatteryOptimizations(packageName) != false -> true
                else -> {
                    if (requestPermission) requestBatteryOptimizationPermission()
                    false
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    @RequiresApi(Build.VERSION_CODES.M)
    private fun Context.requestBatteryOptimizationPermission() {
        try {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ).apply { batteryPermissionResultLauncher.launch(this) }
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
}
