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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.views.SelectBottomSheetDialog

class SortFilesBottomSheetDialog : SelectBottomSheetDialog() {

    private val navigationArgs: SortFilesBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        selectTitle.setText(R.string.sortTitle)

        selectRecyclerView.adapter = SortFilesBottomSheetAdapter(
            selectedType = navigationArgs.sortType,
            usage = navigationArgs.sortTypeUsage,
            onItemClicked = ::onSortTypeClicked,
        )
    }

    private fun onSortTypeClicked(sortType: SortType) {
        setBackNavigationResult(FileListFragment.SORT_TYPE_OPTION_KEY, sortType)
    }
}
