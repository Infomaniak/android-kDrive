/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import com.infomaniak.drive.utils.MatomoUtils.trackCategoriesEvent
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_bottom_sheet_category_info_actions.*
import kotlinx.coroutines.Dispatchers

class CategoryInfoActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private val categoryInfoActionViewModel: CategoryInfoActionViewModel by viewModels()
    private val navigationArgs: CategoryInfoActionsBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_bottom_sheet_category_info_actions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        categoryIcon.setBackgroundColor(Color.parseColor(categoryColor))
        categoryTitle.text = categoryName
        handleRights()
        editCategory.setOnClickListener {
            safeNavigate(
                CategoryInfoActionsBottomSheetDialogDirections.actionCategoryInfoActionsBottomSheetDialogToCreateOrEditCategoryFragment(
                    fileId = fileId,
                    categoryIsPredefined = categoryIsPredefined,
                    categoryId = categoryId,
                    categoryName = if (categoryIsPredefined) "" else categoryName,
                    categoryColor = categoryColor,
                )
            )
        }
        deleteCategory.setOnClickListener {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.buttonDelete),
                message = getString(R.string.modalDeleteCategoryDescription, categoryName),
                autoDismiss = false,
                isDeletion = true,
                buttonText = getString(R.string.buttonDelete),
            ) { dialog ->
                trackCategoriesEvent("delete")
                deleteCategory(categoryId) { dialog.dismiss() }
            }
        }
    }

    private fun handleRights() = with(DriveInfosController.getCategoryRights()) {
        disabledEditCategory.isGone = canEditCategory
        editCategory.isEnabled = canEditCategory

        with(canDeleteCategory && !navigationArgs.categoryIsPredefined) {
            disabledDeleteCategory.isGone = this
            deleteCategory.isEnabled = this
        }
    }

    private fun deleteCategory(categoryId: Int, dismissDialog: () -> Unit) {
        categoryInfoActionViewModel.deleteCategory(categoryId).observe(viewLifecycleOwner) { apiResponse ->
            dismissDialog()
            if (apiResponse.isSuccess()) {
                setBackNavigationResult(DELETE_CATEGORY_NAV_KEY, categoryId)
            } else Utils.showSnackbar(requireView(), apiResponse.translateError())
        }
    }

    internal class CategoryInfoActionViewModel : ViewModel() {

        fun deleteCategory(categoryId: Int): LiveData<ApiResponse<Boolean>> {
            return liveData(Dispatchers.IO) {
                with(ApiRepository.deleteCategory(AccountUtils.currentDriveId, categoryId)) {
                    val response = if (isSuccess() || isAlreadyDeleted(this)) {
                        DriveInfosController.updateDrive { localDrive ->
                            localDrive.categories.find(categoryId)?.deleteFromRealm()
                        }
                        ApiResponse(result = ApiResponse.Status.SUCCESS)
                    } else this
                    emit(response)
                }
            }
        }

        private fun isAlreadyDeleted(apiResponse: ApiResponse<Boolean>): Boolean {
            return apiResponse.result == ApiResponse.Status.ERROR &&
                    apiResponse.error?.code?.equals("object_not_found", true) == true
        }
    }

    companion object {
        const val DELETE_CATEGORY_NAV_KEY = "delete_category_nav_key"
    }
}
