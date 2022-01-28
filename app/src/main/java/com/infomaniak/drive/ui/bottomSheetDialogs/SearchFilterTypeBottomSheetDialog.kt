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
import android.view.View
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.ui.fileList.SearchFiltersViewModel
import com.infomaniak.drive.views.SelectBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select.*

class SearchFilterTypeBottomSheetDialog : SelectBottomSheetDialog() {

    private val searchFiltersViewModel: SearchFiltersViewModel by navGraphViewModels(R.id.searchFiltersFragment)
    private val navigationArgs: SearchFilterTypeBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectTitle.text = getString(R.string.fileTypeFilterTitle)
        selectRecyclerView.adapter = SearchFilterTypeBottomSheetAdapter(
            getSortedTypes(),
            navigationArgs.type,
            onTypeClicked = {
                searchFiltersViewModel.type.value = it
                dismiss()
            },
        )
    }

    private fun getSortedTypes(): List<ConvertedType> {
        return listOf(
            ConvertedType.ARCHIVE,
            ConvertedType.AUDIO,
            ConvertedType.CODE,
            ConvertedType.FOLDER,
            ConvertedType.IMAGE,
            ConvertedType.PDF,
            ConvertedType.PRESENTATION,
            ConvertedType.SPREADSHEET,
            ConvertedType.TEXT,
            ConvertedType.VIDEO,
        ).sortedBy { getString(it.searchFilterName) }
    }
}
