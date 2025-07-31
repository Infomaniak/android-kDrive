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
package com.infomaniak.drive.utils

import android.Manifest.permission.ACCESS_MEDIA_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.DialogInterface
import android.os.Build.VERSION.SDK_INT
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsBottomSheetDialog
import com.infomaniak.drive.utils.DrivePermissions.Type.*
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.lib.core.utils.requestPermissionsIsPossible
import com.infomaniak.lib.core.utils.startAppSettingsConfig
import splitties.init.appCtx
import splitties.systemservices.powerManager

class DrivePermissions(private val type: Type) {

    enum class Type {
        DownloadingWithDownloadManager,
        ReadingMediaForSync,
        UploadInTheBackground,
    }

    private lateinit var registerForActivityResult: ActivityResultLauncher<Array<String>>
    private lateinit var activity: FragmentActivity


    private val requiredPermissions = permissionsFor(type, includeOptionals = false).toTypedArray()
    private val permissionsToAsk = permissionsFor(type, includeOptionals = true).toTypedArray()

    private val requiredPermissionsAlreadyGranted: Boolean = appCtx.hasPermissions(requiredPermissions)

    private val backgroundSyncPermissionsBottomSheetDialog by lazy { BackgroundSyncPermissionsBottomSheetDialog() }

    fun registerPermissions(activity: FragmentActivity, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        this.activity = activity
        registerForActivityResult = activity.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
            if (requiredPermissionsAlreadyGranted.not()) {
                val authorized = authorizedPermissions.filterKeys { it in requiredPermissions }.values.all { it }
                onPermissionResult?.invoke(authorized)
                activity.resultPermissions(authorized, requiredPermissions)
            }
        }
    }

    fun registerPermissions(fragment: Fragment, onPermissionResult: ((authorized: Boolean) -> Unit)? = null) {
        activity = fragment.requireActivity()
        registerForActivityResult =
            fragment.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
                if (requiredPermissionsAlreadyGranted.not()) {
                    val authorized = authorizedPermissions.filterKeys { it in requiredPermissions }.values.all { it }
                    onPermissionResult?.invoke(authorized)
                    activity.resultPermissions(authorized, requiredPermissions)
                }
            }
    }

    fun hasNeededPermissions(requestIfNotGranted: Boolean = false, canShowBatteryDialog: Boolean = true): Boolean {
        if (canShowBatteryDialog) when (type) {
            DownloadingWithDownloadManager -> Unit
            ReadingMediaForSync, UploadInTheBackground -> tryShowBatteryDialogIfNeeded()
        }
        return when {
            activity.hasPermissions(permissionsToAsk) -> true
            else -> {
                if (requestIfNotGranted) {
                    // All permissions (optionals included) are NOT granted (so we are requesting them)…
                    registerForActivityResult.launch(permissionsToAsk)
                }
                activity.hasPermissions(requiredPermissions) // …but the needed ones might be granted.
            }
        }
    }

    private fun tryShowBatteryDialogIfNeeded() {
        val mustDisplayIt = UiSettings(activity).mustDisplayBatteryDialog
        if (mustDisplayIt || powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID).not()) {
            with(backgroundSyncPermissionsBottomSheetDialog) {
                if (dialog?.isShowing != true && !isResumed) {
                    show(this@DrivePermissions.activity.supportFragmentManager, "syncPermissionsDialog")
                }
            }
        }
    }

    companion object {

        @StringRes
        val permissionNeededDescriptionRes = when {
            SDK_INT >= 33 -> R.string.allPermissionNeededAndroid13
            else -> R.string.allPermissionNeeded
        }

        fun permissionsFor(type: Type, includeOptionals: Boolean = false): List<String> = buildList {
            // Note that order is important, because it'll be the ask order.
            when (type) {
                DownloadingWithDownloadManager -> if (SDK_INT < 29) add(WRITE_EXTERNAL_STORAGE)
                ReadingMediaForSync -> {
                    // See https://developer.android.com/training/data-storage/shared/media
                    if (SDK_INT >= 34) add(READ_MEDIA_VISUAL_USER_SELECTED)
                    if (SDK_INT >= 33) {
                        add(READ_MEDIA_IMAGES)
                        add(READ_MEDIA_VIDEO)
                    } else {
                        add(READ_EXTERNAL_STORAGE)
                    }
                    if (SDK_INT >= 29) add(ACCESS_MEDIA_LOCATION)
                    if (SDK_INT >= 33 && includeOptionals) add(POST_NOTIFICATIONS)
                }
                UploadInTheBackground -> if (SDK_INT >= 33 && includeOptionals) add(POST_NOTIFICATIONS)
            }
        }

        fun Activity.resultPermissions(authorized: Boolean, permissions: Array<String>) {
            if (!authorized && !requestPermissionsIsPossible(permissions)) {
                MaterialAlertDialogBuilder(this, R.style.DialogStyle)
                    .setTitle(R.string.androidPermissionTitle)
                    .setMessage(permissionNeededDescriptionRes)
                    .setCancelable(false)
                    .setPositiveButton(R.string.buttonAuthorize) { _: DialogInterface?, _: Int -> startAppSettingsConfig() }
                    .show()
            }
        }
    }
}
