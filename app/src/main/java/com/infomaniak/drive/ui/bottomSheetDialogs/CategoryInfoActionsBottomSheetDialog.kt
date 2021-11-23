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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_ACTION_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_MAIN_KEY
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.CANCELLABLE_TITLE_KEY
import com.infomaniak.drive.utils.setBackNavigationResult
import kotlinx.android.synthetic.main.fragment_bottom_sheet_category_info_actions.*

class CategoryInfoActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private val navigationArgs: CategoryInfoActionsBottomSheetDialogArgs by navArgs()

//    private val mainViewModel: MainViewModel by activityViewModels()
//
//    private lateinit var currentFile: File
//    private lateinit var drivePermissions: DrivePermissions

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_bottom_sheet_category_info_actions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryTitle.text = navigationArgs.categoryName
        categoryIcon.setBackgroundColor(Color.parseColor(navigationArgs.categoryColor))

        val categoryRights = DriveInfosController.getCategoryRights()
        editCategory.isVisible = categoryRights?.canEditCategory == true
        deleteCategory.isVisible = categoryRights?.canDeleteCategory == true

        editCategory.setOnClickListener { Log.e("TOTO", "editCategory") }
        deleteCategory.setOnClickListener { Log.e("TOTO", "deleteCategory") }
    }

    private fun transmitActionAndPopBack(message: String, action: CancellableAction? = null) {
        val bundle = bundleOf(CANCELLABLE_TITLE_KEY to message, CANCELLABLE_ACTION_KEY to action)
        setBackNavigationResult(CANCELLABLE_MAIN_KEY, bundle)
    }
}
