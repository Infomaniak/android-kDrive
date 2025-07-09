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
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackCategoriesEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.databinding.FragmentBottomSheetCategoryInfoActionsBinding
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.find
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.ApiResponseStatus
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.SnackbarUtils
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setBackNavigationResult
import kotlinx.coroutines.Dispatchers

class CategoryInfoActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private val categoryInfoActionViewModel: CategoryInfoActionViewModel by viewModels()
    private val navigationArgs: CategoryInfoActionsBottomSheetDialogArgs by navArgs()

    private var binding: FragmentBottomSheetCategoryInfoActionsBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetCategoryInfoActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        binding.categoryIcon.setBackgroundColor(Color.parseColor(categoryColor))
        binding.categoryTitle.text = categoryName
        handleRights()
        binding.editCategory.setOnClickListener {
            safeNavigate(
                CategoryInfoActionsBottomSheetDialogDirections.actionCategoryInfoActionsBottomSheetDialogToCreateOrEditCategoryFragment(
                    filesIds = filesIds,
                    categoryIsPredefined = categoryIsPredefined,
                    categoryId = categoryId,
                    categoryName = if (categoryIsPredefined) "" else categoryName,
                    categoryColor = categoryColor,
                )
            )
        }
        binding.deleteCategory.setOnClickListener {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.buttonDelete),
                message = getString(R.string.modalDeleteCategoryDescription, categoryName),
                autoDismiss = false,
                isDeletion = true,
                buttonText = getString(R.string.buttonDelete),
            ) { dialog ->
                trackCategoriesEvent(MatomoName.Delete)
                deleteCategory(categoryId) { dialog.dismiss() }
            }
        }
    }

    private fun handleRights() = with(binding) {
        val categoryRights = DriveInfosController.getCategoryRights()
        disabledEditCategory.isGone = categoryRights.canEdit
        editCategory.isEnabled = categoryRights.canEdit

        with(categoryRights.canDelete && !navigationArgs.categoryIsPredefined) {
            disabledDeleteCategory.isGone = this
            deleteCategory.isEnabled = this
        }
    }

    private fun deleteCategory(categoryId: Int, dismissDialog: () -> Unit) {
        categoryInfoActionViewModel.deleteCategory(categoryId).observe(viewLifecycleOwner) { apiResponse ->
            dismissDialog()
            if (apiResponse.isSuccess()) {
                setBackNavigationResult(DELETE_CATEGORY_NAV_KEY, categoryId)
            } else SnackbarUtils.showSnackbar(requireView(), apiResponse.translateError())
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
                        ApiResponse(result = ApiResponseStatus.SUCCESS)
                    } else this
                    emit(response)
                }
            }
        }

        private fun isAlreadyDeleted(apiResponse: ApiResponse<Boolean>): Boolean {
            return apiResponse.result == ApiResponseStatus.ERROR &&
                    apiResponse.error?.code?.equals("object_not_found", true) == true
        }
    }

    companion object {
        const val DELETE_CATEGORY_NAV_KEY = "delete_category_nav_key"
    }
}
