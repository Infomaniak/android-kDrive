/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.ui.view.edgetoedge.EdgeToEdgeBottomSheetDialog
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentBottomSheetBackgroundSyncBinding
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.powerManager

class BackgroundSyncPermissionsBottomSheetDialog : EdgeToEdgeBottomSheetDialog() {

    private var binding: FragmentBottomSheetBackgroundSyncBinding by safeBinding()

    private val permissionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val hasPermission = it.resultCode == RESULT_OK || checkBatteryLifePermission(requestPermission = false)
        onPermissionGranted(hasPermission)
    }

    private var hasDoneNecessary = false
    private var hadBatteryLifePermission = false

    private val bottomSheetBehavior by lazy { (dialog as BottomSheetDialog).behavior }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetBackgroundSyncBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setAllowBackgroundSyncSwitch(checkBatteryLifePermission(requestPermission = false))

        with(binding) {
            allowBackgroundSyncSwitch.setOnCheckedChangeListener { _, isChecked -> setAllowBackgroundSyncSwitch(isChecked) }
            hasDoneNecessaryCheckbox.setOnCheckedChangeListener { _, isChecked -> hasDoneNecessary = isChecked }
            actionButton.setOnClickListener { dismiss() }
            openGuideButton.setOnClickListener { context?.openUrl(SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL) }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        UiSettings(requireActivity()).mustDisplayBatteryDialog = if (manufacturerWarning) !hasDoneNecessary else false
        super.onDismiss(dialog)
    }

    private fun onPermissionGranted(hasPermission: Boolean) {
        setAllowBackgroundSyncSwitch(hasPermission)
        onHasBatteryPermission(hasPermission)
        if (hasPermission && !manufacturerWarning) hasDoneNecessary = true
    }

    private fun checkBatteryLifePermission(requestPermission: Boolean): Boolean {
        val hasPermission = powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
        if (hasPermission.not() && requestPermission) {
            requestBatteryOptimizationPermission()
        }
        onHasBatteryPermission(hasPermission)
        return hasPermission
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationPermission(): Job = viewLifecycleOwner.lifecycleScope.launch {
        // Ensure this Fragment is in the resumed state (and that the Fragment is attached) before
        // launch is called on the permissionResultLauncher, to avoid undocumented IllegalStateException.
        viewLifecycleOwner.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }

        val packageName = appCtx.packageName
        runCatching {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri()
            ).apply { permissionResultLauncher.launch(this) }
        }
            .cancellable()
            .recoverCatching { exception ->
                if (exception is ActivityNotFoundException) {
                    permissionResultLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    Sentry.captureException(exception) { scope -> scope.level = SentryLevel.INFO }
                }
            }
            .onFailure(Sentry::captureException)
    }

    private fun onHasBatteryPermission(hasBatteryPermission: Boolean) {
        hadBatteryLifePermission = hasBatteryPermission
        showManufacturerWarning(hasBatteryPermission)

        if (hasBatteryPermission) bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun showManufacturerWarning(hasBatteryPermission: Boolean) {
        binding.guideCard.isVisible = hasBatteryPermission && manufacturerWarning
    }

    private fun setAllowBackgroundSyncSwitch(hasBatteryPermission: Boolean) {
        binding.allowBackgroundSyncSwitch.apply {
            isChecked = hasBatteryPermission
            isClickable = !hasBatteryPermission
        }
        if (hasBatteryPermission && !hadBatteryLifePermission) checkBatteryLifePermission(requestPermission = true)
    }

    companion object {

        private val EVIL_MANUFACTURERS = arrayOf("asus", "huawei", "lenovo", "meizu", "oneplus", "oppo", "vivo", "xiaomi")

        val manufacturerWarning = EVIL_MANUFACTURERS.contains(Build.MANUFACTURER.lowercase()) || BuildConfig.DEBUG

        private const val SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL = "https://faq.infomaniak.com/2685"

    }
}
