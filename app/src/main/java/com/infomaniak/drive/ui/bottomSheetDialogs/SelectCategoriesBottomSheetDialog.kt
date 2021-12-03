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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UICategory
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_select_categories.*

class SelectCategoriesBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: SelectCategoriesBottomSheetDialogArgs by navArgs()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()

    private lateinit var adapter: CategoriesAdapter
    private lateinit var file: File

    private var aCategoryHasBeenModified: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_select_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FileController.getFileById(navigationArgs.fileId).let {
            if (it == null) {
                findNavController().popBackStack()
                return
            } else {
                file = it
            }
        }

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
                setBackNavResult()
                true
            } else {
                false
            }
        }

        val categoryRights = DriveInfosController.getCategoryRights()
        setToolbar(categoryRights)
        setAdapter(categoryRights)
        setupBackActionHandler()
        updateUI(file.categories.map { it.id }.toTypedArray(), file.id)
    }

    private fun setToolbar(categoryRights: CategoryRights?) {
        toolbar.apply {
            menu.findItem(R.id.addCategory).isVisible = categoryRights?.canCreateCategory == true
            setOnMenuItemClickListener { menuItem ->
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
                } else {
                    false
                }
            }
            setNavigationOnClickListener { setBackNavResult() }
        }
    }

    private fun setAdapter(categoryRights: CategoryRights?) {
        adapter = CategoriesAdapter(
            onCategoryChanged = { categoryId, isSelected -> onCategoryChanged(categoryId, isSelected) }
        ).apply {
            canEditCategory = categoryRights?.canEditCategory ?: false
            canDeleteCategory = categoryRights?.canDeleteCategory ?: false
        }

        categoriesRecyclerView.adapter = adapter
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Bundle>(CreateOrEditCategoryBottomSheetDialog.CREATE_CATEGORY_NAV_KEY) { bundle ->
            bundle.apply {
                val id = getInt(CreateOrEditCategoryBottomSheetDialog.CATEGORY_ID_BUNDLE_KEY)
                val name = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_NAME_BUNDLE_KEY) ?: return@apply
                val color = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_COLOR_BUNDLE_KEY) ?: return@apply
                adapter.addCategory(id, name, color)
            }
        }

        getBackNavigationResult<Bundle>(CreateOrEditCategoryBottomSheetDialog.EDIT_CATEGORY_NAV_KEY) { bundle ->
            aCategoryHasBeenModified = true
            bundle.apply {
                val id = getInt(CreateOrEditCategoryBottomSheetDialog.CATEGORY_ID_BUNDLE_KEY)
                val name = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_NAME_BUNDLE_KEY)
                val color = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_COLOR_BUNDLE_KEY)
                adapter.editCategory(id, name, color)
            }
        }

        getBackNavigationResult<Int>(CategoryInfoActionsBottomSheetDialog.DELETE_CATEGORY_NAV_KEY) { categoryId ->
            aCategoryHasBeenModified = true
            adapter.deleteCategory(categoryId)
        }
    }

    private fun updateUI(enabledCategoriesIds: Array<Int>, fileId: Int) {
        val allCategories = DriveInfosController.getCurrentDriveCategories()
        val uiCategories = allCategories.map { category ->
            UICategory(
                id = category.id,
                name = category.getName(requireContext()),
                color = category.color,
                isPredefined = category.isPredefined ?: true,
                isSelected = enabledCategoriesIds.find { it == category.id } != null
            )
        }
        adapter.setAll(uiCategories.sortCategoriesList())
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

    private fun onCategoryChanged(categoryId: Int, isSelected: Boolean) {
        val requestLiveData = with(selectCategoriesViewModel) {
            if (isSelected) {
                addCategory(file.id, file.driveId, categoryId)
            } else {
                removeCategory(file, categoryId)
            }
        }

        requestLiveData.observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                adapter.updateCategory(categoryId, isSelected)
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
            }
        }
    }

    private fun setBackNavResult() {
        setBackNavigationResult(
            SELECT_CATEGORIES_NAV_KEY,
            bundleOf(
                CATEGORIES_BUNDLE_KEY to adapter.categories.filter { it.isSelected }.map { it.id },
                MODIFIED_CATEGORY_BUNDLE_KEY to aCategoryHasBeenModified,
            )
        )
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
        const val CATEGORIES_BUNDLE_KEY = "categories_bundle_key"
        const val MODIFIED_CATEGORY_BUNDLE_KEY = "modified_category_bundle_key"
    }
}
