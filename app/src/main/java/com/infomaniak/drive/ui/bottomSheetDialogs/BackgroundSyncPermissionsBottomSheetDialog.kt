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

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentBottomSheetBackgroundSyncBinding
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.lib.core.utils.UtilsUi.openUrl

class BackgroundSyncPermissionsBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentBottomSheetBackgroundSyncBinding
    private val drivePermissions = DrivePermissions()
    private var hasDoneNecessary = false
    private var isWhitelisted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBottomSheetBackgroundSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drivePermissions.registerBatteryPermission(this) { hasPermission -> onPermissionGranted(hasPermission) }

        shouldBeWhiteListed(checkWhitelisted(false))

        with(binding) {
            allowBackgroundSyncSwitch.setOnCheckedChangeListener { _, isChecked -> shouldBeWhiteListed(isChecked) }
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
        shouldBeWhiteListed(hasPermission)
        showManufacturerWarning(hasPermission)
        if (hasPermission && !manufacturerWarning) hasDoneNecessary = true
    }

    private fun checkWhitelisted(requestPermission: Boolean): Boolean {
        return drivePermissions.checkBatteryLifePermission(requestPermission).also { hasPermission ->
            showManufacturerWarning(hasPermission)
        }
    }

    private fun showManufacturerWarning(hasBatteryPermission: Boolean) {
        isWhitelisted = hasBatteryPermission
        binding.guideCard.isVisible = hasBatteryPermission && manufacturerWarning
    }

    private fun shouldBeWhiteListed(shouldBeWhitelisted: Boolean) {
        binding.allowBackgroundSyncSwitch.apply {
            isChecked = shouldBeWhitelisted
            isClickable = !shouldBeWhitelisted
        }
        if (shouldBeWhitelisted && !isWhitelisted) checkWhitelisted(true)
    }

    companion object {

        private val EVIL_MANUFACTURERS = arrayOf("asus", "huawei", "lenovo", "meizu", "oneplus", "oppo", "vivo", "xiaomi")

        val manufacturerWarning = EVIL_MANUFACTURERS.contains(Build.MANUFACTURER.lowercase()) || BuildConfig.DEBUG

        private const val SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL = "https://faq.infomaniak.com/2685"

    }
}
