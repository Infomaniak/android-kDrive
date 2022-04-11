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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsViewModel.Companion.manufacturerWarning
import com.infomaniak.drive.ui.bottomSheetDialogs.BackgroundSyncPermissionsViewModel.Companion.updateShowBatteryOptimizationDialog
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

        backgroundSyncPermissionsViewModel.apply {
            val isWhiteListed = checkWhitelisted(false)
            allowBackgroundSyncSwitch.isClickable = !isWhiteListed
            shouldBeWhitelisted.value = isWhiteListed

            shouldBeWhitelisted.observe(viewLifecycleOwner) { shouldBeWhitelisted ->
                allowBackgroundSyncSwitch.apply {
                    isChecked = shouldBeWhitelisted
                    isClickable = !shouldBeWhitelisted
                }
                if (shouldBeWhitelisted && isWhitelisted.value != true) checkWhitelisted(true)
            }
            hasDoneNecessary.observe(viewLifecycleOwner) { hasDoneNecessary ->
                val mustShowBatteryDialog = !(hasDoneNecessary && isWhitelisted.value == true)
                activity?.updateShowBatteryOptimizationDialog(mustShowBatteryDialog)
            }

            hasDoneNecessaryCheckbox.setOnCheckedChangeListener { _, isChecked -> hasDoneNecessary.value = isChecked }
            allowBackgroundSyncSwitch.setOnCheckedChangeListener { _, isChecked -> shouldBeWhitelisted.value = isChecked }
        }

        actionButton.setOnClickListener { dismiss() }
        openGuideButton.setOnClickListener { context?.openUrl(SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL) }
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
            showManufacturerWarning(hasPermission)
        }
    }

    private fun showManufacturerWarning(hasBatteryPermission: Boolean) {
        backgroundSyncPermissionsViewModel.apply {
            isWhitelisted.value = hasBatteryPermission
            guideCard.visibility = if (hasBatteryPermission && manufacturerWarning) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val SUPPORT_FAQ_BACKGROUND_FUNCTIONING_URL = "https://faq.infomaniak.com/2685"
    }
}
