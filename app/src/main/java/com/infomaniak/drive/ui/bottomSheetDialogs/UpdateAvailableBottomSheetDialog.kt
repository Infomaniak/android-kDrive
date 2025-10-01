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
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.extensions.goToPlayStore
import com.infomaniak.core.legacy.stores.StoresSettingsRepository
import com.infomaniak.core.legacy.stores.StoresViewModel
import com.infomaniak.core.legacy.utils.getAppName
import com.infomaniak.drive.R

class UpdateAvailableBottomSheetDialog : InformationBottomSheetDialog() {

    private val storesViewModel: StoresViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        storesViewModel.isUpdateBottomSheetShown = true

        title.setText(R.string.updateAvailableTitle)
        description.text = getString(R.string.updateAvailableDescription, requireContext().getAppName())
        illu.setAnimation(R.raw.illu_upgrade)

        secondaryActionButton.setOnClickListener {
            storesViewModel.set(StoresSettingsRepository.IS_USER_WANTING_UPDATES_KEY, false)
            dismiss()
        }

        actionButton.apply {
            setText(R.string.buttonUpdate)
            setOnClickListener {
                storesViewModel.set(StoresSettingsRepository.IS_USER_WANTING_UPDATES_KEY, true)
                requireContext().goToPlayStore()
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        storesViewModel.isUpdateBottomSheetShown = false
        super.onDismiss(dialog)
    }
}
