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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.setBackNavigationResult
import kotlinx.android.synthetic.main.fragment_bottom_sheet_search_filter_file_type.*

open class SearchFilterFileTypeBottomSheetDialog : BottomSheetDialogFragment() {

    private val searchFiltersViewModel: SearchFiltersViewModel by navGraphViewModels(R.id.searchFiltersBottomSheetDialog)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_search_filter_file_type, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filters = listOf<Triple<View, View, File.ConvertedType>>(
            Triple(imageFilterLayout, imageFilterEndIcon, File.ConvertedType.IMAGE),
            Triple(videoFilterLayout, videoFilterEndIcon, File.ConvertedType.VIDEO),
            Triple(audioFilterLayout, audioFilterEndIcon, File.ConvertedType.AUDIO),
            Triple(pdfFilterLayout, pdfFilterEndIcon, File.ConvertedType.PDF),
            Triple(docsFilterLayout, docsFilterEndIcon, File.ConvertedType.TEXT),
            Triple(gridsFilterLayout, gridsFilterEndIcon, File.ConvertedType.SPREADSHEET),
            Triple(pointsFilterLayout, pointsFilterEndIcon, File.ConvertedType.PRESENTATION),
            Triple(folderFilterLayout, folderFilterEndIcon, File.ConvertedType.FOLDER),
            Triple(archiveFilterLayout, archiveFilterEndIcon, File.ConvertedType.ARCHIVE),
            Triple(codeFilterLayout, codeFilterEndIcon, File.ConvertedType.CODE),
        )

        // Set previously selected Type
        filters.find { it.third == searchFiltersViewModel.type }?.second?.isVisible = true

        filters.forEach { filter ->
            filter.first.setOnClickListener {
                searchFiltersViewModel.type = filter.third
                setBackNavigationResult(SEARCH_FILTER_TYPE_NAV_KEY, true)
            }
        }
    }

    companion object {
        const val SEARCH_FILTER_TYPE_NAV_KEY = "search_filter_type_nav_key"
    }
}
