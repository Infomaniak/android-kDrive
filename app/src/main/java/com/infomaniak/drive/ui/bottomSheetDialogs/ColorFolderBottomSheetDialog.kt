/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.uiview.edgetoedge.EdgeToEdgeBottomSheetDialog
import com.infomaniak.drive.databinding.FragmentBottomSheetColorFolderBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.ColorFolderAdapter.Companion.COLORS

class ColorFolderBottomSheetDialog : EdgeToEdgeBottomSheetDialog() {

    private var binding: FragmentBottomSheetColorFolderBinding by safeBinding()
    private val navigationArgs: ColorFolderBottomSheetDialogArgs by navArgs()

    private lateinit var colorsAdapter: ColorFolderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetColorFolderBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
    }

    private fun setAdapter() {
        colorsAdapter = ColorFolderAdapter(
            onColorSelected = { color: String -> setBackNavigationResult(COLOR_FOLDER_NAV_KEY, color) }
        ).apply {
            selectedPosition = COLORS.indexOfFirst { it == navigationArgs.color }
        }.also {
            binding.colorsRecyclerView.adapter = it
        }
    }

    companion object {
        const val COLOR_FOLDER_NAV_KEY = "color_folder_nav_key"
    }
}
