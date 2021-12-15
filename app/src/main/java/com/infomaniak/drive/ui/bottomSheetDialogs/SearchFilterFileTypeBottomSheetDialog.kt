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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.utils.setBackNavigationResult
import kotlinx.android.synthetic.main.fragment_bottom_sheet_search_filter_file_type.*
import kotlinx.android.synthetic.main.view_search_filter_type.view.*

open class SearchFilterFileTypeBottomSheetDialog : BottomSheetDialogFragment() {

    private val navigationArgs: SearchFilterFileTypeBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_search_filter_file_type, container, false)
    }

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val types = listOf(
            Pair(ConvertedType.ARCHIVE, R.drawable.ic_file_zip),
            Pair(ConvertedType.AUDIO, R.drawable.ic_file_audio),
            Pair(ConvertedType.CODE, R.drawable.ic_file_code),
            Pair(ConvertedType.FOLDER, R.drawable.ic_folder_filled),
            Pair(ConvertedType.IMAGE, R.drawable.ic_file_image),
            Pair(ConvertedType.PDF, R.drawable.ic_file_pdf),
            Pair(ConvertedType.PRESENTATION, R.drawable.ic_file_presentation),
            Pair(ConvertedType.SPREADSHEET, R.drawable.ic_file_grids),
            Pair(ConvertedType.TEXT, R.drawable.ic_file_text),
            Pair(ConvertedType.VIDEO, R.drawable.ic_file_video),
        ).sortedBy { getString(it.first.searchFilterName) }

        types.forEach { type ->
            searchTypeFilerContainer.addView(
                layoutInflater.inflate(R.layout.view_search_filter_type, null).apply {
                    typeFilterStartIcon.setImageResource(type.second)
                    typeFilterText.setText(type.first.searchFilterName)
                    typeFilterEndIcon.isVisible = type.first.value == navigationArgs.type?.value
                    setOnClickListener { setBackNavigationResult(SEARCH_FILTER_TYPE_NAV_KEY, type.first) }
                }
            )
        }
    }

    companion object {
        const val SEARCH_FILTER_TYPE_NAV_KEY = "search_filter_type_nav_key"
    }
}
