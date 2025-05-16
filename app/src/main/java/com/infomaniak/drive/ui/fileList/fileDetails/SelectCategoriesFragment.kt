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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.MatomoDrive.trackCategoriesEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.databinding.FragmentSelectCategoriesBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.SelectedState
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UiCategory
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode.MANAGED_CATEGORIES
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode.SELECTED_CATEGORIES
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.getName
import com.infomaniak.drive.utils.setCornersRadius
import com.infomaniak.drive.views.DebouncingTextWatcher
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import java.util.Date

class SelectCategoriesFragment : Fragment() {

    private var binding: FragmentSelectCategoriesBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: SelectCategoriesFragmentArgs by navArgs()

    private lateinit var categoriesAdapter: CategoriesAdapter

    private val driveId: Int by lazy { navigationArgs.userDrive?.driveId ?: AccountUtils.currentDriveId }
    private val usageMode: CategoriesUsageMode by lazy { navigationArgs.categoriesUsageMode }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSelectCategoriesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        selectCategoriesViewModel.init(
            usageMode,
            categories,
            filesIds,
            userDrive ?: UserDrive()
        ).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                with(selectCategoriesViewModel.categoryRights) {
                    setCategoriesAdapter(canEdit, canDelete)
                    setAddCategoryButton(canCreate)
                    configureSearchView(canCreate)
                }

                configureToolbar()
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { setBackNavResult() }
                binding.createCategoryRow.setOnClickListener { navigateToCreateCategory() }
                setBackActionHandlers()
            } else {
                findNavController().popBackStack()
            }
        }

        binding.root.enableEdgeToEdge(withBottom = false) {
            binding.categoriesRecyclerView.setMargins(bottom = it.bottom)
        }
    }

    private fun setCategoriesAdapter(canEditCategory: Boolean, canDeleteCategory: Boolean) {
        categoriesAdapter = CategoriesAdapter(
            onCategoryChanged = { id, isSelected -> manageCategory(id, isAdding = isSelected) }
        ).apply {
            this.canEditCategory = canEditCategory
            this.canDeleteCategory = canDeleteCategory
            when (usageMode) {
                SELECTED_CATEGORIES -> updateSelectedCategoriesModeUi()
                else -> updateFileCategoriesModeUi()
            }
            binding.categoriesRecyclerView.adapter = this
        }
    }

    private fun setAddCategoryButton(canCreateCategory: Boolean) {
        binding.toolbar.menu.findItem(R.id.addCategory).isVisible = canCreateCategory
    }

    private fun configureToolbar() = with(binding.toolbar) {
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

    private fun configureSearchView(canCreateCategory: Boolean) = with(binding.searchViewCard) {
        searchView.hint = getString(R.string.searchTitle)
        clearButton.setOnClickListener { searchView.setText("") }
        setSearchViewTextChangedListener(canCreateCategory)
        setSearchViewEditorActionListener()
    }

    private fun setSearchViewTextChangedListener(canCreateCategory: Boolean) = with(binding.searchViewCard) {
        searchView.addTextChangedListener(DebouncingTextWatcher(lifecycle) {
            if (isResumed) {
                clearButton.isInvisible = it.isNullOrEmpty()
                categoriesAdapter.updateFilter(searchView.text.toString())
                configureCreateCategoryRow(canCreateCategory, it?.trim())
            }
        })
    }

    private fun setSearchViewEditorActionListener() = with(binding.searchViewCard.searchView) {
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

    private fun CategoriesAdapter.updateFileCategoriesModeUi() {
        val uiCategories = DriveInfosController.getDriveCategories(driveId).map { category ->
            val fileCategory = selectCategoriesViewModel.filesCategories.find { it.categoryId == category.id }
            createUiCategory(
                category = category,
                selectedState = if (fileCategory == null) SelectedState.NOT_SELECTED else SelectedState.SELECTED,
                addedToFileAt = fileCategory?.addedAt,
            )
        }

        setItems(uiCategories, usageMode)

        onMenuClicked = { category ->
            with(category) {
                safeNavigate(
                    R.id.categoryInfoActionsBottomSheetDialog,
                    CategoryInfoActionsBottomSheetDialogArgs(
                        filesIds = navigationArgs.filesIds,
                        categoryId = id,
                        categoryName = name,
                        categoryColor = color,
                        categoryIsPredefined = isPredefined,
                    ).toBundle(),
                )
            }
        }
    }

    private fun CategoriesAdapter.updateSelectedCategoriesModeUi() {
        val uiCategories = DriveInfosController.getDriveCategories(driveId).map { category ->
            val selectedCategory = selectCategoriesViewModel.selectedCategories.find { it.id == category.id }
            createUiCategory(
                category = category,
                selectedState = if (selectedCategory == null) SelectedState.NOT_SELECTED else SelectedState.SELECTED,
            )
        }

        setItems(uiCategories, usageMode)
    }

    private fun createUiCategory(category: Category, selectedState: SelectedState, addedToFileAt: Date? = null): UiCategory {
        return UiCategory(
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
                filesIds = navigationArgs.filesIds,
                categoryId = CreateOrEditCategoryFragment.CREATE_CATEGORY_ID,
                categoryName = binding.searchViewCard.searchView.text.toString(),
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
            binding.addCategoryTitle.text = HtmlCompat.fromHtml(
                getString(R.string.manageCategoriesCreateTitle, "<b>$it</b>"),
                HtmlCompat.FROM_HTML_MODE_COMPACT,
            )
        }
    }

    private fun setCreateCategoryRowCorners() {
        val radius = resources.getDimension(R.dimen.cardViewRadius)
        val topCornerRadius = if (categoriesAdapter.filteredCategories.isEmpty()) radius else 0.0f
        binding.createCategoryRow.setCornersRadius(topCornerRadius, radius)
    }

    private fun setCreateCategoryRowVisibility(categoryName: String?) = with(binding) {
        val isVisible = categoryName?.isNotBlank() == true && !categoriesAdapter.doesCategoryExist(categoryName)
        categoriesAdapter.isCreateRowVisible = isVisible
        createCategoryRow.isVisible = isVisible
        createCategoryRowSeparator.isVisible = isVisible && categoriesAdapter.filteredCategories.isNotEmpty()
    }

    private fun manageCategory(categoryId: Int, isAdding: Boolean) {
        trackCategoriesEvent(if (isAdding) "assign" else "remove")
        if (usageMode == SELECTED_CATEGORIES) {
            categoriesAdapter.selectCategory(categoryId, isAdding, usageMode)
            return
        }

        mainViewModel.manageCategory(
            categoryId = categoryId,
            files = selectCategoriesViewModel.selectedFiles,
            isAdding = isAdding
        ).observe(viewLifecycleOwner) { apiResponse ->
            updateAdapterAfterAddingOrRemovingCategory(categoryId, apiResponse, isAdding)
        }
    }

    private fun updateAdapterAfterAddingOrRemovingCategory(id: Int, apiResponse: ApiResponse<*>, isAdding: Boolean) {
        val isSelected = if (apiResponse.isSuccess()) {
            isAdding
        } else {
            SnackbarUtils.showSnackbar(requireView(), apiResponse.translateError())
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
