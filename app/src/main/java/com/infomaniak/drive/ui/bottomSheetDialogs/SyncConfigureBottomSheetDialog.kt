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

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.safeNavigate
import com.google.android.material.R as RMaterial

class SyncConfigureBottomSheetDialog : InformationBottomSheetDialog() {

    private val onGlobalLayoutListener = OnGlobalLayoutListener {
        (dialog as BottomSheetDialog?)?.let { bottomSheetDialog ->
            bottomSheetDialog.findViewById<FrameLayout>(RMaterial.id.design_bottom_sheet)?.let { bottomSheetView ->
                BottomSheetBehavior.from(bottomSheetView).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    peekHeight = 0
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        title.setText(R.string.syncConfigureTitle)
        description.text = getString(R.string.syncConfigureDescription, AccountUtils.getCurrentDrive()?.name)

        illu.setAnimation(R.raw.illu_photos)

        actionButton.apply {
            setText(R.string.buttonConfigure)
            setOnClickListener {
                trackEvent("syncModal", "configure")
                safeNavigate(R.id.syncSettingsActivity)
                dismiss()
            }
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        super.onDestroyView()
    }
}
