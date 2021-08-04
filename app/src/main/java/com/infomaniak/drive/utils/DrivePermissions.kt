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
package com.infomaniak.drive.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.requestPermissionsIsPossible
import com.infomaniak.lib.core.utils.startAppSettingsConfig

class DrivePermissions {

    companion object {
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1
    }

    private lateinit var registerForActivityResult: ActivityResultLauncher<String>
    private lateinit var activity: FragmentActivity

    fun registerPermissions(activity: FragmentActivity, onPermissionResult: ((autorized: Boolean) -> Unit)? = null) {
        this.activity = activity
        registerForActivityResult = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorized ->
            resultPermissions(onPermissionResult, autorized)
        }
    }

    fun registerPermissions(fragment: Fragment, onPermissionResult: ((autorized: Boolean) -> Unit)? = null) {
        this.activity = fragment.requireActivity()
        registerForActivityResult = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorized ->
            resultPermissions(onPermissionResult, autorized)
        }
    }

    private fun resultPermissions(onPermissionResult: ((autorized: Boolean) -> Unit)?, autorized: Boolean) {
        onPermissionResult?.invoke(autorized)
        if (!autorized && !activity.requestPermissionsIsPossible(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            MaterialAlertDialogBuilder(activity, R.style.DialogStyle)
                .setTitle(R.string.androidPermissionTitle)
                .setMessage(R.string.allPermissionNeeded)
                .setPositiveButton(R.string.buttonAuthorize) { _: DialogInterface?, _: Int -> activity.startAppSettingsConfig() }
                .show()
        }
    }


    /**
     * Check if the sync has all permissions to work
     * @return [Boolean] true if the sync has all permissions or false
     */
    fun checkSyncPermissions(): Boolean {
        activity.batteryLifePermission()
        return checkWriteStoragePermission()
    }

    /**
     * Checks if the user has already confirmed write permission
     */
    @SuppressLint("NewApi")
    fun checkWriteStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
            activity.hasPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) -> true
            else -> {
                registerForActivityResult.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                false
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun Context.batteryLifePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            when (this) {
                is Fragment -> startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                is Activity -> startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            }
        }
    }
}