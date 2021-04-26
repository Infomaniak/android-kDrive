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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class DropBoxResultBottomSheetDialog : InformationBottomSheetDialog() {

    val arguments: DropBoxResultBottomSheetDialogArgs by navArgs()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title.text = getString(R.string.dropBoxResultTitle, arguments.name)
        description.setText(R.string.dropBoxResultDescription)

        illu.layoutParams.height = 80.toPx()
        illu.layoutParams.width = 80.toPx()
        illu.setImageResource(R.drawable.ic_folder_dropbox)

        urlDisplay.visibility = VISIBLE
        urlDisplay.setUrl(arguments.url)

        secondaryActionButton.visibility = GONE
        actionButton.setText(R.string.buttonLater)
        actionButton.setOnClickListener {
            dismiss()
        }
    }
}