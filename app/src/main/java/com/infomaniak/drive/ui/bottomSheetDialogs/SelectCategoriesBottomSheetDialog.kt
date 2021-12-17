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

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UICategory
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.android.synthetic.main.item_search_view.*

class SelectCategoriesBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: SelectCategoriesBottomSheetDialogArgs by navArgs()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()

    private lateinit var adapter: CategoriesAdapter
    private lateinit var file: File
    private var hasCategoryBeenModified: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_select_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        file = FileController.getFileById(navigationArgs.fileId) ?: run {
            findNavController().popBackStack()
            return
        }

        with(DriveInfosController.getCategoryRights()) {
            setCategoriesAdapter(this?.canEditCategory == true, this?.canDeleteCategory == true)
            setData()
            setStates(this?.canCreateCategory == true)
            setListeners()
            setBackActionHandlers()
        }
    }

    private fun setCategoriesAdapter(canEditCategory: Boolean, canDeleteCategory: Boolean) {
        adapter = CategoriesAdapter(
            onCategoryChanged = { categoryId, isSelected ->
                if (isSelected) addCategory(categoryId) else removeCategory(categoryId)
            }
        ).apply {
            this.canEditCategory = canEditCategory
            this.canDeleteCategory = canDeleteCategory

            val uiCategories = DriveInfosController.getCurrentDriveCategories().map { category ->
                val fileCategory = file.categories.find { it.id == category.id }
                UICategory(
                    id = category.id,
                    name = category.getName(requireContext()),
                    color = category.color,
                    isPredefined = category.isPredefined ?: true,
                    isSelected = fileCategory != null,
                    userUsageCount = category.userUsageCount,
                    addedToFileAt = fileCategory?.addedToFileAt,
                )
            }
            setAll(uiCategories.sortCategoriesList())

            onMenuClicked = { category ->
                safeNavigate(
                    R.id.categoryInfoActionsBottomSheetDialog, bundleOf(
                        "fileId" to file.id,
                        "categoryId" to category.id,
                        "categoryName" to category.name,
                        "categoryColor" to category.color,
                        "categoryIsPredefined" to category.isPredefined,
                    )
                )
            }
        }

        categoriesRecyclerView.adapter = adapter
    }

    private fun setData() {
        searchView.hint = getString(R.string.searchTitle)
    }

    private fun setStates(canCreateCategory: Boolean) {
        toolbar.menu.findItem(R.id.addCategory).isVisible = canCreateCategory
    }

    private fun setListeners() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
                setBackNavResult()
                true
            } else false
        }

        with(toolbar) {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.addCategory) {
                    navigateToCreateCategory()
                    true
                } else false
            }
            setNavigationOnClickListener { setBackNavResult() }
        }

        createCategoryRow.setOnClickListener { navigateToCreateCategory() }

        with(searchView) {
            clearButton.setOnClickListener { text = null }
            addTextChangedListener(DebouncingTextWatcher(lifecycle) {
                clearButton.isInvisible = it.isNullOrEmpty()
                adapter.updateFilter(text.toString())
                handleCreateCategoryRow(it?.trim())
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                    adapter.updateFilter(text.toString())
                    true
                } else false
            }
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Bundle>(CreateOrEditCategoryBottomSheetDialog.CREATE_CATEGORY_NAV_KEY) { bundle ->
            with(bundle) {
                adapter.addCategory(
                    id = getInt(CreateOrEditCategoryBottomSheetDialog.CATEGORY_ID_BUNDLE_KEY),
                    name = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_NAME_BUNDLE_KEY)!!,
                    color = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_COLOR_BUNDLE_KEY)!!,
                )
            }
        }

        getBackNavigationResult<Bundle>(CreateOrEditCategoryBottomSheetDialog.EDIT_CATEGORY_NAV_KEY) { bundle ->
            hasCategoryBeenModified = true
            with(bundle) {
                adapter.editCategory(
                    id = getInt(CreateOrEditCategoryBottomSheetDialog.CATEGORY_ID_BUNDLE_KEY),
                    name = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_NAME_BUNDLE_KEY),
                    color = getString(CreateOrEditCategoryBottomSheetDialog.CATEGORY_COLOR_BUNDLE_KEY),
                )
            }
        }

        getBackNavigationResult<Int>(CategoryInfoActionsBottomSheetDialog.DELETE_CATEGORY_NAV_KEY) { categoryId ->
            hasCategoryBeenModified = true
            adapter.deleteCategory(categoryId)
        }
    }

    private fun navigateToCreateCategory() {
        with(searchView) {
            val categoryName = text.toString()
            setText("")
            safeNavigate(
                SelectCategoriesBottomSheetDialogDirections.actionSelectCategoriesBottomSheetDialogToCreateOrEditCategoryBottomSheetDialog(
                    fileId = file.id,
                    driveId = file.driveId,
                    categoryId = CreateOrEditCategoryBottomSheetDialog.CREATE_CATEGORY_ID,
                    categoryName = categoryName,
                    categoryColor = null,
                )
            )
        }
    }

    private fun handleCreateCategoryRow(categoryName: String?) {

        val text = getString(R.string.manageCategoriesCreateTitle, "<b>$categoryName</b>")
        addCategoryTitle.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        } else Html.fromHtml(text)

        with(createCategoryRow) {
            var topCornerRadius = 0.0f
            if (adapter.filteredCategories.isEmpty()) {
                topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
                createCategoryRowSeparator.isGone = true
            } else createCategoryRowSeparator.isVisible = true
            val bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)

            shapeAppearanceModel = shapeAppearanceModel
                .toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .build()

            isVisible = categoryName?.isNotBlank() == true && !adapter.doesCategoryExist(categoryName)
        }
    }

    private fun addCategory(categoryId: Int) {
        selectCategoriesViewModel.addCategory(file.id, file.driveId, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                adapter.updateCategory(categoryId, true)
            } else Utils.showSnackbar(requireView(), apiResponse.translateError())
        }
    }

    private fun removeCategory(categoryId: Int) {
        selectCategoriesViewModel.removeCategory(file, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                adapter.updateCategory(categoryId, false)
            } else Utils.showSnackbar(requireView(), apiResponse.translateError())
        }
    }

    private fun setBackNavResult() {
        setBackNavigationResult(
            SELECT_CATEGORIES_NAV_KEY, bundleOf(
                CATEGORIES_BUNDLE_KEY to adapter.allCategories.filter { it.isSelected }.map { it.id },
                MODIFIED_CATEGORY_BUNDLE_KEY to hasCategoryBeenModified,
            )
        )
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
        const val CATEGORIES_BUNDLE_KEY = "categories_bundle_key"
        const val MODIFIED_CATEGORY_BUNDLE_KEY = "modified_category_bundle_key"
    }
}
