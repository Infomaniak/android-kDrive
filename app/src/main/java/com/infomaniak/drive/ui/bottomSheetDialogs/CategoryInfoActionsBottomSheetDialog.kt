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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_bottom_sheet_category_info_actions.*
import kotlinx.coroutines.Dispatchers

class CategoryInfoActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private val navigationArgs: CategoryInfoActionsBottomSheetDialogArgs by navArgs()
    private val categoryInfoActionViewModel: CategoryInfoActionViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_category_info_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        val driveId = AccountUtils.currentDriveId
        val categoryRights = DriveInfosController.getCategoryRights()
        val canEditCategory = categoryRights?.canEditCategory ?: false
        val canDeleteCategory = categoryRights?.canDeleteCategory ?: false

        categoryTitle.text = categoryName
        categoryIcon.setBackgroundColor(Color.parseColor(categoryColor))

        editCategory.isEnabled = canEditCategory
        disabledEditCategory.isGone = canEditCategory

        with(canDeleteCategory && !categoryIsPredefined) {
            deleteCategory.isEnabled = this
            disabledDeleteCategory.isGone = this
        }

        editCategory.setOnClickListener {
            safeNavigate(
                CategoryInfoActionsBottomSheetDialogDirections.actionCategoryInfoActionsBottomSheetDialogToCreateOrEditCategoryBottomSheetDialog(
                    fileId = fileId,
                    driveId = driveId,
                    categoryIsPredefined = categoryIsPredefined,
                    categoryId = categoryId,
                    categoryName = if (categoryIsPredefined) "" else categoryName,
                    categoryColor = categoryColor,
                )
            )
        }

        deleteCategory.setOnClickListener {
            Utils.confirmCategoryDeletion(requireContext(), categoryName) { dialog ->
                deleteCategory(driveId, categoryId) { dialog.dismiss() }
            }
        }

        getBackNavigationResult<Bundle>(CreateOrEditCategoryBottomSheetDialog.EDIT_CATEGORY_NAV_KEY) { bundle ->
            setBackNavigationResult(CreateOrEditCategoryBottomSheetDialog.EDIT_CATEGORY_NAV_KEY, bundle)
        }
    }

    private fun deleteCategory(driveId: Int, categoryId: Int, dismissDialog: () -> Unit) {
        categoryInfoActionViewModel.deleteCategory(driveId, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            dismissDialog()
            if (apiResponse.isSuccess()) {
                setBackNavigationResult(DELETE_CATEGORY_NAV_KEY, categoryId)
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
            }
        }
    }

    internal class CategoryInfoActionViewModel : ViewModel() {
        fun deleteCategory(driveId: Int, categoryId: Int): LiveData<ApiResponse<Boolean>> =
            liveData(Dispatchers.IO) {
                val apiResponse = ApiRepository.deleteCategory(driveId, categoryId)
                if (apiResponse.isSuccess()) {
                    DriveInfosController.updateDrive { localDrive ->
                        val category = localDrive.categories.find { it.id == categoryId }
                        localDrive.categories.remove(category)
                    }
                }
                emit(apiResponse)
            }
    }

    companion object {
        const val DELETE_CATEGORY_NAV_KEY = "delete_category_nav_key"
    }
}
