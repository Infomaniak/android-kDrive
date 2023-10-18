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
import android.view.View
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.lib.core.utils.getAppName
import com.infomaniak.lib.core.utils.goToPlayStore

class UpdateAvailableBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.setText(R.string.updateAvailableTitle)
        description.text = getString(R.string.updateAvailableDescription, requireContext().getAppName())
        illu.setAnimation(R.raw.illu_upgrade)

        secondaryActionButton.setOnClickListener {
            UiSettings(requireContext()).updateLater = true
            dismiss()
        }

        actionButton.apply {
            setText(R.string.buttonUpdate)
            setOnClickListener {
                UiSettings(requireContext()).updateLater = false
                requireContext().goToPlayStore()
                dismiss()
            }
        }
    }
}
