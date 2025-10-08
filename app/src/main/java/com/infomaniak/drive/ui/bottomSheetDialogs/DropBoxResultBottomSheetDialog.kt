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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.drive.R

class DropBoxResultBottomSheetDialog : InformationBottomSheetDialog() {

    private val arguments: DropBoxResultBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        title.text = getString(R.string.dropBoxResultTitle, arguments.name)
        description.setText(R.string.dropBoxResultDescription)

        illu.layoutParams.height = 80.toPx()
        illu.layoutParams.width = 80.toPx()
        illu.setImageResource(R.drawable.ic_folder_dropbox)

        urlDisplay.isVisible = true
        urlDisplay.setUrl(arguments.url)

        secondaryActionButton.isGone = true
        actionButton.setText(R.string.buttonLater)
        actionButton.setOnClickListener { dismiss() }
    }
}
