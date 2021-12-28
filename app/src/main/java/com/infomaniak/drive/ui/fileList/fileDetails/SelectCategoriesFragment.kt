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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectCategoriesBottomSheetDialog.UsageMode.*
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.ui.bottomSheetDialogs.CategoryInfoActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.UICategory
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment.UsageMode.*
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.DebouncingTextWatcher
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.android.synthetic.main.item_search_view.*

class SelectCategoriesFragment : Fragment() {

    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: SelectCategoriesFragmentArgs by navArgs()

    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var usageMode: UsageMode
    private lateinit var file: File
    private lateinit var selectedCategories: List<Category>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_select_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeData()
        if (usageMode == NO_CATEGORIES) {
            findNavController().popBackStack()
            return
        }

        DriveInfosController.getCategoryRights()?.apply {
            if (usageMode == SELECTED_CATEGORIES) {
                canCreateCategory = false
                canDeleteCategory = false
                canEditCategory = false
            }
        }.let {
            setCategoriesAdapter(it?.canEditCategory == true, it?.canDeleteCategory == true)
            setAddCategoryButton(it?.canCreateCategory == true)
        }

        searchView.hint = getString(R.string.searchTitle)

        setOnClickListeners()
        setBackActionHandlers()
    }

    private fun initializeData() {
        with(navigationArgs) {
            val tempFile = FileController.getFileById(fileId)
            usageMode = if (fileId == -1 || tempFile == null) {
                if (categories != null) {
                    selectedCategories = DriveInfosController.getCurrentDriveCategoriesFromIds(categories?.toTypedArray()!!)
                    SELECTED_CATEGORIES
                } else {
                    NO_CATEGORIES
                }
            } else {
                file = tempFile
                FILE_CATEGORIES
            }
        }
    }

    private fun setCategoriesAdapter(canEditCategory: Boolean, canDeleteCategory: Boolean) {
        categoriesAdapter = CategoriesAdapter(
            onCategoryChanged = { categoryId, isSelected ->
                if (isSelected) addCategory(categoryId) else removeCategory(categoryId)
            }
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

    private fun setOnClickListeners() {
        toolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.addCategory) {
                    navigateToCreateCategory()
                    true
                } else false
            }
            setNavigationOnClickListener { setBackNavResult() }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { setBackNavResult() }

        createCategoryRow.setOnClickListener { navigateToCreateCategory() }

        searchView.apply {
            clearButton.setOnClickListener { text = null }
            addTextChangedListener(DebouncingTextWatcher(lifecycle) {
                if (isAtLeastResumed()) {
                    clearButton.isInvisible = it.isNullOrEmpty()
                    categoriesAdapter.updateFilter(text.toString())
                    handleCreateCategoryRow(it?.trim())
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (EditorInfo.IME_ACTION_SEARCH == actionId) {
                    categoriesAdapter.updateFilter(text.toString())
                    true
                } else false
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
            UICategory(
                id = category.id,
                name = category.getName(requireContext()),
                color = category.color,
                isPredefined = category.isPredefined,
                isSelected = fileCategory != null,
                userUsageCount = category.userUsageCount,
                addedToFileAt = fileCategory?.addedToFileAt,
            )
        }
        setItems(uiCategories, usageMode)

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

    private fun CategoriesAdapter.updateSelectedCategoriesModeUI() {
        val uiCategories = DriveInfosController.getCurrentDriveCategories().map { category ->
            val selectedCategory = selectedCategories.find { it.id == category.id }
            UICategory(
                id = category.id,
                name = category.getName(requireContext()),
                color = category.color,
                isPredefined = category.isPredefined,
                isSelected = selectedCategory != null,
                userUsageCount = category.userUsageCount,
                addedToFileAt = null,
            )
        }
        setItems(uiCategories, usageMode)
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

    private fun handleCreateCategoryRow(categoryName: String?) {
        if (usageMode != FILE_CATEGORIES) return

        createCategoryRowSeparator.isGone = categoriesAdapter.filteredCategories.isEmpty()

        addCategoryTitle.text = HtmlCompat.fromHtml(
            getString(R.string.manageCategoriesCreateTitle, "<b>$categoryName</b>"),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
        )

        createCategoryRow.apply {
            setCornersRadius()
            isVisible = categoryName?.isNotBlank() == true && !categoriesAdapter.doesCategoryExist(categoryName)
        }
    }

    private fun MaterialCardView.setCornersRadius() {
        val topCornerRadius = if (categoriesAdapter.filteredCategories.isEmpty()) {
            resources.getDimension(R.dimen.cardViewRadius)
        } else 0.0f
        val bottomCornerRadius = resources.getDimension(R.dimen.cardViewRadius)
        shapeAppearanceModel = shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .build()
    }

    private fun addCategory(categoryId: Int) {
        if (usageMode == SELECTED_CATEGORIES) {
            categoriesAdapter.selectCategory(categoryId, true, usageMode)
            return
        }

        selectCategoriesViewModel.addCategory(file, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            val isSelected = if (apiResponse.isSuccess()) {
                true
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
                false
            }
            categoriesAdapter.selectCategory(categoryId, isSelected, usageMode)
        }
    }

    private fun removeCategory(categoryId: Int) {
        if (usageMode == SELECTED_CATEGORIES) {
            categoriesAdapter.selectCategory(categoryId, false, usageMode)
            return
        }

        selectCategoriesViewModel.removeCategory(file, categoryId).observe(viewLifecycleOwner) { apiResponse ->
            val isSelected = if (apiResponse.isSuccess()) {
                false
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
                true
            }
            categoriesAdapter.selectCategory(categoryId, isSelected, usageMode)
        }
    }

    private fun setBackNavResult() {
        setBackNavigationResult(
            SELECT_CATEGORIES_NAV_KEY,
            categoriesAdapter.allCategories.filter { it.isSelected }.map { it.id },
        )
    }

    private fun isAtLeastResumed(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    enum class UsageMode {
        FILE_CATEGORIES,
        SELECTED_CATEGORIES,
        NO_CATEGORIES,
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "select_categories_nav_key"
    }
}
