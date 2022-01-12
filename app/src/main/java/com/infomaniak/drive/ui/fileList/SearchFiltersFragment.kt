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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchCategoriesOwnershipFilter
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.getBackNavigationResult
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_search_filters.*

class SearchFiltersFragment : Fragment() {

    private val searchFiltersViewModel: SearchFiltersViewModel by navGraphViewModels(R.id.searchFiltersFragment)
    private val navigationArgs: SearchFiltersFragmentArgs by navArgs()

    private val rights = DriveInfosController.getCategoryRights()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_search_filters, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeFilters()
        handleRights()
        setToolbar()
        setDateAndTypeFilters()
        setCategoriesOwnershipFilters()
        setClearButton()
        setSaveButton()
        listenToFiltersUpdates()
    }

    private fun initializeFilters() = with(searchFiltersViewModel) {
        if (date.value == null) date.value = navigationArgs.date
        if (type.value == null) type.value = navigationArgs.type?.let(ConvertedType::valueOf)
        if (categories == null) {
            categories = navigationArgs.categories?.toTypedArray()?.let(DriveInfosController::getCurrentDriveCategoriesFromIds)
        }
        if (categoriesOwnership == null) categoriesOwnership = navigationArgs.categoriesOwnership
        updateAllFiltersUI()
    }

    private fun handleRights() {
        val isVisible = rights?.canReadCategoryOnFile == true
        categoriesTitle.isVisible = isVisible
        chooseCategoriesFilter.isVisible = isVisible
    }

    private fun setToolbar() {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setDateAndTypeFilters() = with(searchFiltersViewModel) {
        dateFilter.setOnClickListener { safeNavigate(R.id.searchFilterDateDialog, bundleOf("date" to date.value)) }
        typeFilter.setOnClickListener { safeNavigate(R.id.searchFilterTypeDialog, bundleOf("type" to type.value)) }
    }

    private fun setCategoriesOwnershipFilters() = with(searchFiltersViewModel) {
        belongToAllCategoriesFilter.setOnClickListener {
            categoriesOwnership = SearchCategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
            updateCategoriesOwnershipUI()
        }
        belongToOneCategoryFilter.setOnClickListener {
            categoriesOwnership = SearchCategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
            updateCategoriesOwnershipUI()
        }
    }

    private fun setClearButton() = with(searchFiltersViewModel) {
        clearButton.setOnClickListener {
            clearFilters()
            updateAllFiltersUI()
        }
    }

    private fun setSaveButton() = with(searchFiltersViewModel) {
        saveButton.setOnClickListener {
            setBackNavigationResult(
                SEARCH_FILTERS_NAV_KEY, bundleOf(
                    SEARCH_FILTERS_DATE_BUNDLE_KEY to date.value,
                    SEARCH_FILTERS_TYPE_BUNDLE_KEY to type.value,
                    SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY to categories?.map { it.id }?.toIntArray(),
                    SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY to categoriesOwnership,
                )
            )
        }
    }

    private fun listenToFiltersUpdates() = with(searchFiltersViewModel) {
        date.observe(viewLifecycleOwner) { updateDateUI() }
        type.observe(viewLifecycleOwner) { updateTypeUI() }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            categories = if (it.isEmpty()) null else DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray())
            updateAllFiltersUI()
        }
    }

    private fun updateAllFiltersUI() {
        updateDateUI()
        updateTypeUI()
        updateCategoriesUI()
        updateCategoriesOwnershipUI()
    }

    private fun updateDateUI() = with(dateFilterText) {
        searchFiltersViewModel.date.value?.let { text = it.text }
            ?: run { setText(R.string.searchFiltersSelectDate) }
    }

    private fun updateTypeUI() {
        searchFiltersViewModel.type.value?.let {
            typeFilterStartIcon.setImageResource(it.icon)
            typeFilterText.setText(it.searchFilterName)
        } ?: run {
            typeFilterStartIcon.setImageResource(R.drawable.ic_file)
            typeFilterText.setText(R.string.searchFiltersSelectType)
        }
    }

    private fun updateCategoriesUI() {
        val categories = searchFiltersViewModel.categories ?: emptyList()
        categoriesContainer.setup(
            categories = categories,
            canPutCategoryOnFile = rights?.canPutCategoryOnFile ?: false,
            layoutInflater = layoutInflater,
            onClicked = {
                safeNavigate(
                    SearchFiltersFragmentDirections.actionSearchFiltersFragmentToSelectCategoriesFragment(
                        categories = categories.map { it.id }.toIntArray(),
                    )
                )
            }
        )
    }

    private fun updateCategoriesOwnershipUI() = with(searchFiltersViewModel) {
        val isVisible = categories?.isNotEmpty() == true
        belongToAllCategoriesFilter.isVisible = isVisible
        belongToOneCategoryFilter.isVisible = isVisible

        val belongToAll = categoriesOwnership == SearchCategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
        belongToAllCategoriesFilter.setupSelection(belongToAll)
        belongToOneCategoryFilter.setupSelection(!belongToAll)
    }

    private fun MaterialCardView.setupSelection(enabled: Boolean) {
        strokeWidth = if (enabled) 2.toPx() else 0
        invalidate()
    }

    companion object {
        const val SEARCH_FILTERS_NAV_KEY = "search_filters_nav_key"
        const val SEARCH_FILTERS_DATE_BUNDLE_KEY = "search_filters_date_bundle_key"
        const val SEARCH_FILTERS_TYPE_BUNDLE_KEY = "search_filters_type_bundle_key"
        const val SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY = "search_filters_categories_bundle_key"
        const val SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY = "search_filters_categories_ownership_bundle_key"
    }
}
