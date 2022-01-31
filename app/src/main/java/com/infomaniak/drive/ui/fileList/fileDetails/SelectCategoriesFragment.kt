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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.core.text.HtmlCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.SelectedState
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UICategory
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode.MANAGED_CATEGORIES
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode.SELECTED_CATEGORIES
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.android.synthetic.main.item_search_view.*
import java.util.*

class SelectCategoriesFragment : Fragment() {

    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: SelectCategoriesFragmentArgs by navArgs()

    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var usageMode: CategoriesUsageMode
    private lateinit var file: File
    private lateinit var selectedCategories: List<Category>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_select_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(navigationArgs) {
            usageMode = categoriesUsageMode

            if (usageMode == SELECTED_CATEGORIES) {
                selectedCategories =
                    DriveInfosController.getCurrentDriveCategoriesFromIds(categories?.toTypedArray() ?: arrayOf())
            } else {
                file = FileController.getFileById(fileId) ?: run {
                    findNavController().popBackStack()
                    return
                }
            }
        }

        val categoryRights = if (usageMode == MANAGED_CATEGORIES) DriveInfosController.getCategoryRights() else CategoryRights()
        with(categoryRights) {
            setCategoriesAdapter(canEditCategory, canDeleteCategory)
            setAddCategoryButton(canCreateCategory)
            configureSearchView(canCreateCategory)
        }

        configureToolbar()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { setBackNavResult() }
        createCategoryRow.setOnClickListener { navigateToCreateCategory() }
        setBackActionHandlers()
    }

    private fun setCategoriesAdapter(canEditCategory: Boolean, canDeleteCategory: Boolean) {
        categoriesAdapter = CategoriesAdapter(
            onCategoryChanged = { id, isSelected -> if (isSelected) addCategory(id) else removeCategory(id) }
        ).apply {
            this.canEditCategory = canEditCategory
            this.canDeleteCategory = canDeleteCategory
            when (usageMode) {
                SELECTED_CATEGORIES -> updateSelectedCategoriesModeUI()
                else -> updateFileCategoriesModeUI()
            }
            categoriesRecyclerView.adapter = this
        }
    }

    private fun setAddCategoryButton(canCreateCategory: Boolean) {
        toolbar.menu.findItem(R.id.addCategory).isVisible = canCreateCategory
    }

    private fun configureToolbar() = with(toolbar) {
        setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.addCategory) {
                navigateToCreateCategory()
                true
            } else {
                false
            }
        }
        setNavigationOnClickListener { setBackNavResult() }
    }

    private fun configureSearchView(canCreateCategory: Boolean) {
        searchView.hint = getString(R.string.searchTitle)
        clearButton.setOnClickListener { searchView.setText("") }
        setSearchViewTextChangedListener(canCreateCategory)
        setSearchViewEditorActionListener()
    }

    private fun setSearchViewTextChangedListener(canCreateCategory: Boolean) = with(searchView) {
        addTextChangedListener(DebouncingTextWatcher(lifecycle) {
            if (isResumed) {
                clearButton.isInvisible = it.isNullOrEmpty()
                categoriesAdapter.updateFilter(text.toString())
                configureCreateCategoryRow(canCreateCategory, it?.trim())
            }
        })
    }

    private fun setSearchViewEditorActionListener() = with(searchView) {
        setOnEditorActionListener { _, actionId, _ ->
            if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                categoriesAdapter.updateFilter(text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Int>(CategoryInfoActionsBottomSheetDialog.DELETE_CATEGORY_NAV_KEY) { categoryId ->
            categoriesAdapter.deleteCategory(categoryId)
        }
    }

    private fun CategoriesAdapter.updateFileCategoriesModeUI() {
        val uiCategories = DriveInfosController.getCurrentDriveCategories().map { category ->
            val fileCategory = file.categories.find { it.id == category.id }
            createUICategory(
                category = category,
                selectedState = if (fileCategory == null) SelectedState.NOT_SELECTED else SelectedState.SELECTED,
                addedToFileAt = fileCategory?.addedToFileAt,
            )
        }

        setItems(uiCategories, usageMode)

        onMenuClicked = { category ->
            with(category) {
                safeNavigate(
                    R.id.categoryInfoActionsBottomSheetDialog,
                    CategoryInfoActionsBottomSheetDialogArgs(
                        fileId = file.id,
                        categoryId = id,
                        categoryName = name,
                        categoryColor = color,
                        categoryIsPredefined = isPredefined,
                    ).toBundle(),
                )
            }
        }
    }

    private fun CategoriesAdapter.updateSelectedCategoriesModeUI() {
        val uiCategories = DriveInfosController.getCurrentDriveCategories().map { category ->
            val selectedCategory = selectedCategories.find { it.id == category.id }
            createUICategory(
                category = category,
                selectedState = if (selectedCategory == null) SelectedState.NOT_SELECTED else SelectedState.SELECTED,
            )
        }

        setItems(uiCategories, usageMode)
    }

    private fun createUICategory(category: Category, selectedState: SelectedState, addedToFileAt: Date? = null): UICategory {
        return UICategory(
            id = category.id,
            name = category.getName(requireContext()),
            color = category.color,
            isPredefined = category.isPredefined,
            selectedState = selectedState,
            userUsageCount = category.userUsageCount,
            addedToFileAt = addedToFileAt,
        )
    }

    private fun navigateToCreateCategory() {
        safeNavigate(
            SelectCategoriesFragmentDirections.actionSelectCategoriesFragmentToCreateOrEditCategoryFragment(
                fileId = file.id,
                categoryId = CreateOrEditCategoryFragment.CREATE_CATEGORY_ID,
                categoryName = searchView.text.toString(),
                categoryColor = null,
            )
        )
    }

    private fun configureCreateCategoryRow(canCreateCategory: Boolean, categoryName: String?) {
        if (usageMode == MANAGED_CATEGORIES && canCreateCategory) {
            setCreateCategoryRowTitle(categoryName)
            setCreateCategoryRowCorners()
            setCreateCategoryRowVisibility(categoryName)
        }
    }

    private fun setCreateCategoryRowTitle(categoryName: String?) {
        categoryName?.let {
            addCategoryTitle.text = HtmlCompat.fromHtml(
                getString(R.string.manageCategoriesCreateTitle, "<b>$it</b>"),
                HtmlCompat.FROM_HTML_MODE_COMPACT,
            )
        }
    }

    private fun setCreateCategoryRowCorners() {
        val radius = resources.getDimension(R.dimen.cardViewRadius)
        val topCornerRadius = if (categoriesAdapter.filteredCategories.isEmpty()) radius else 0.0f
        createCategoryRow.setCornersRadius(topCornerRadius, radius)
    }

    private fun setCreateCategoryRowVisibility(categoryName: String?) {
        val isVisible = categoryName?.isNotBlank() == true && !categoriesAdapter.doesCategoryExist(categoryName)
        categoriesAdapter.isCreateRowVisible = isVisible
        createCategoryRow.isVisible = isVisible
        createCategoryRowSeparator.isVisible = isVisible && categoriesAdapter.filteredCategories.isNotEmpty()
    }

    private fun addCategory(id: Int) {
        if (usageMode == SELECTED_CATEGORIES) {
            categoriesAdapter.selectCategory(id, true, usageMode)
            return
        }

        selectCategoriesViewModel.addCategory(file, id).observe(viewLifecycleOwner) { apiResponse ->
            updateAdapterAfterAddingOrRemovingCategory(id, apiResponse, true)
        }
    }

    private fun removeCategory(id: Int) {
        if (usageMode == SELECTED_CATEGORIES) {
            categoriesAdapter.selectCategory(id, false, usageMode)
            return
        }

        selectCategoriesViewModel.removeCategory(file, id).observe(viewLifecycleOwner) { apiResponse ->
            updateAdapterAfterAddingOrRemovingCategory(id, apiResponse, false)
        }
    }

    private fun updateAdapterAfterAddingOrRemovingCategory(id: Int, apiResponse: ApiResponse<Unit>, isAdding: Boolean) {
        val isSelected = if (apiResponse.isSuccess()) {
            isAdding
        } else {
            Utils.showSnackbar(requireView(), apiResponse.translateError())
            !isAdding
        }
        categoriesAdapter.selectCategory(id, isSelected, usageMode)
    }

    private fun setBackNavResult() {
        setBackNavigationResult(
            SELECT_CATEGORIES_NAV_KEY,
            categoriesAdapter.allCategories.filter { it.selectedState == SelectedState.SELECTED }.map { it.id },
        )
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
    }
}
