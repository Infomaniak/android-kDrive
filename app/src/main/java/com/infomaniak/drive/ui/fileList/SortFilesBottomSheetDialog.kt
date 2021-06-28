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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.setBackNavigationResult
import kotlinx.android.synthetic.main.fragment_bottom_sheet_sort_files.*

class SortFilesBottomSheetDialog : BottomSheetDialogFragment() {

    private val navigationArgs: SortFilesBottomSheetDialogArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bottom_sheet_sort_files, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sortAz.setOnClickListener {
            onSortTypeClicked(File.SortType.NAME_AZ)
        }
        sortZa.setOnClickListener {
            onSortTypeClicked(File.SortType.NAME_ZA)
        }
        sortMostRecent.setOnClickListener {
            onSortTypeClicked(File.SortType.RECENT)
        }
        sortOlder.setOnClickListener {
            onSortTypeClicked(File.SortType.OLDER)
        }
        sortBigger.setOnClickListener {
            onSortTypeClicked(File.SortType.BIGGER)
        }
        sortSmaller.setOnClickListener {
            onSortTypeClicked(File.SortType.SMALLER)
        }
        sortExtension.setOnClickListener {
            // TODO implement extension sort
            onSortTypeClicked(File.SortType.EXTENSION)
        }
        updateCurrentSortTypeIcon(navigationArgs.sortType)
    }

    private fun onSortTypeClicked(sortType: File.SortType) {
        updateCurrentSortTypeIcon(sortType)
        setBackNavigationResult(FileListFragment.SORT_TYPE_OPTION_KEY, sortType, true)
    }

    private fun updateCurrentSortTypeIcon(sortType: File.SortType) {
        val itemList = arrayListOf(
            File.SortType.NAME_AZ to sortAzActiveIcon,
            File.SortType.NAME_ZA to sortZaActiveIcon,
            File.SortType.RECENT to sortMostRecentActiveIcon,
            File.SortType.RECENT_TRASHED to sortMostRecentActiveIcon,
            File.SortType.OLDER to sortOlderActiveIcon,
            File.SortType.OLDER_TRASHED to sortOlderActiveIcon,
            File.SortType.BIGGER to sortBiggerActiveIcon,
            File.SortType.SMALLER to sortSmallerActiveIcon,
            File.SortType.EXTENSION to sortExtensionActiveIcon
        )

        itemList.forEach {
            if (it.first == sortType) it.second.visibility = VISIBLE
            else it.second.visibility = GONE
        }
    }
}