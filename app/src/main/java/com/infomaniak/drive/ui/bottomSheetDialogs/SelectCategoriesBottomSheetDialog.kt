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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.coroutines.Dispatchers
import java.util.*

data class UICategory(
    val id: Int,
    val name: String,
    val color: String,
    val isPredefined: Boolean,
    var isSelected: Boolean,
)

class SelectCategoriesBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: SelectCategoriesBottomSheetDialogArgs by navArgs()

    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()

    private lateinit var adapter: CategoriesAdapter
    private lateinit var file: File

    private var aCategoryHasBeenModified: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_select_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun setBackNavResult() {
            setBackNavigationResult(
                SELECT_CATEGORIES_NAV_KEY,
                bundleOf(
                    CATEGORIES_BUNDLE_KEY to adapter.categories.filter { it.isSelected }.map { it.id },
                    MODIFIED_CATEGORY_BUNDLE_KEY to aCategoryHasBeenModified,
                )
            )
        }

        FileController.getFileById(navigationArgs.fileId).let {
            if (it == null) {
                Utils.showSnackbar(requireView(), R.string.anErrorHasOccurred)
                findNavController().popBackStack()
                return
            } else {
                file = it
            }
        }

        val categoryRights = DriveInfosController.getCategoryRights()

        toolbar.menu.findItem(R.id.addCategory).isVisible = categoryRights?.canCreateCategory == true
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.addCategory) {
                safeNavigate(
                    SelectCategoriesBottomSheetDialogDirections.actionSelectCategoriesBottomSheetDialogToCreateOrEditCategoryBottomSheetDialog(
                        fileId = file.id,
                        driveId = file.driveId,
                        categoryId = CreateOrEditCategoryBottomSheetDialog.NO_PREVIOUS_CATEGORY_ID,
                        categoryName = null,
                        categoryColor = null,
                    )
                )
                true
            } else
                false
        }
        toolbar.setNavigationOnClickListener { setBackNavResult() }

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
                setBackNavResult()
                true
            } else false
        }

        getBackNavigationResult<Int>(CreateOrEditCategoryBottomSheetDialog.CREATE_CATEGORY_NAV_KEY) { categoryId ->

            val oldIds = adapter.categories.filter { it.isSelected }.map { it.id }

            val ids = mutableListOf<Int>()
            ids.addAll(oldIds)
            ids.add(categoryId)

            updateUI(ids.toTypedArray(), file.id)
        }

        getBackNavigationResult<Int>(CategoryInfoActionsBottomSheetDialog.DELETE_CATEGORY_NAV_KEY) { categoryId ->

            aCategoryHasBeenModified = true

            val ids = adapter.categories.filter { it.isSelected && it.id != categoryId }.map { it.id }

            updateUI(ids.toTypedArray(), file.id)
        }

        getBackNavigationResult<List<Int>?>(CategoryInfoActionsBottomSheetDialog.EDIT_CATEGORY_NAV_KEY) { ids ->

            aCategoryHasBeenModified = true

            if (ids != null) {
                updateUI(ids.toTypedArray(), file.id)
            }
        }

        adapter = CategoriesAdapter(onCategoryChanged = { categoryId, isSelected, position ->

            val requestLiveData = with(selectCategoriesViewModel) {
                if (isSelected) addCategory(file.id, file.driveId, categoryId)
                else removeCategory(file, categoryId)
            }

            requestLiveData.observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    adapter.categories.find { it.id == categoryId }?.isSelected = isSelected
                    adapter.setAll(adapter.categories)
                } else {
                    Utils.showSnackbar(requireView(), apiResponse.translateError())
                }
            }
        })

        adapter.canEditCategory = categoryRights?.canEditCategory ?: false
        adapter.canDeleteCategory = categoryRights?.canDeleteCategory ?: false

        categoriesRecyclerView.adapter = adapter

        updateUI(navigationArgs.categoriesIds.toTypedArray(), file.id)
    }

    private fun updateUI(enabledCategoriesIds: Array<Int>, fileId: Int) {

        val allCategories = DriveInfosController.getCategories()
        val enabledCategories = DriveInfosController.getCategories(enabledCategoriesIds)

        val uiCategories = allCategories
            .map { category ->
                UICategory(
                    id = category.id,
                    name = category.getName(requireContext()),
                    color = category.color,
                    isPredefined = category.isPredefined ?: true,
                    isSelected = enabledCategories.find { it.id == category.id } != null
                )
            }

        adapter.setAll(uiCategories)

        adapter.onMenuClicked = { category ->
            val bundle = bundleOf(
                "fileId" to fileId,
                "categoryId" to category.id,
                "categoryName" to category.name,
                "categoryColor" to category.color,
                "categoryIsPredefined" to category.isPredefined,
            )
            safeNavigate(R.id.categoryInfoActionsBottomSheetDialog, bundle)
        }
    }

    internal class SelectCategoriesViewModel : ViewModel() {

        fun addCategory(fileId: Int, driveId: Int, categoryId: Int): LiveData<ApiResponse<Unit>> = liveData(Dispatchers.IO) {

            val apiResponse = ApiRepository.addCategory(fileId, driveId, mapOf("id" to categoryId))

            if (apiResponse.isSuccess()) {
                FileController.updateFile(fileId) { localFile ->
                    localFile.categories.add(
                        FileCategory(
                            id = categoryId,
                            userId = AccountUtils.currentUserId,
                            addedToFileAt = Date()
                        )
                    )
                }
            }

            emit(apiResponse)
        }

        fun removeCategory(file: File, categoryId: Int): LiveData<ApiResponse<Unit>> = liveData(Dispatchers.IO) {

            val apiResponse = ApiRepository.removeCategory(file, categoryId)

            if (apiResponse.isSuccess()) {
                FileController.updateFile(file.id) { localFile ->
                    val categories = localFile.categories
                    val category = categories.find { it.id == categoryId }
                    categories.remove(category)
                }
            }

            emit(apiResponse)
        }
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
        const val CATEGORIES_BUNDLE_KEY = "categories_bundle_key"
        const val MODIFIED_CATEGORY_BUNDLE_KEY = "modified_category_bundle_key"
    }
}
