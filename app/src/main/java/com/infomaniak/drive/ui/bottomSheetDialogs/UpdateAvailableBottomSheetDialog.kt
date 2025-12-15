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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import com.infomaniak.core.extensions.appName
import com.infomaniak.core.extensions.goToAppStore
import com.infomaniak.core.inappupdate.AppUpdateSettingsRepository
import com.infomaniak.core.inappupdate.updatemanagers.InAppUpdateManager
import com.infomaniak.drive.MatomoDrive
import com.infomaniak.drive.MatomoDrive.trackInAppUpdate
import com.infomaniak.drive.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UpdateAvailableBottomSheetDialog : InformationBottomSheetDialog() {
    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        inAppUpdateManager.isUpdateBottomSheetShown = true

        title.setText(R.string.updateAvailableTitle)
        description.text = getString(R.string.updateAvailableDescription, appName)
        illu.setAnimation(R.raw.illu_upgrade)

        secondaryActionButton.setOnClickListener {
            inAppUpdateManager.set(AppUpdateSettingsRepository.IS_USER_WANTING_UPDATES_KEY, false)
            dismiss()
        }

        actionButton.apply {
            setText(R.string.buttonUpdate)
            setOnClickListener {
                inAppUpdateManager.set(AppUpdateSettingsRepository.IS_USER_WANTING_UPDATES_KEY, true)
                requireContext().goToAppStore()
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        trackInAppUpdate(MatomoDrive.MatomoName.DiscoverLater)
        inAppUpdateManager.isUpdateBottomSheetShown = false
        super.onDismiss(dialog)
    }
}
