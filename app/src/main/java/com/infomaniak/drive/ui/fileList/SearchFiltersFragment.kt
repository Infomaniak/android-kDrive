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
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.CategoriesOwnershipFilter
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterDateBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterFileTypeBottomSheetDialog
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.getBackNavigationResult
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_search_filters.*

class SearchFiltersFragment : Fragment() {

    private val searchFiltersViewModel: SearchFiltersViewModel by viewModels()
    private val navigationArgs: SearchFiltersFragmentArgs by navArgs()

    private val rights = DriveInfosController.getCategoryRights()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_search_filters, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setData()
        setStates()
        setListeners()
        setBackActionHandlers()
    }

    private fun setData() = with(searchFiltersViewModel) {
        date = navigationArgs.date
        type = navigationArgs.type?.let(ConvertedType::valueOf)
        categories = navigationArgs.categories?.let { DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray()) }
        categoriesOwnership = navigationArgs.categoriesOwnership ?: SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE
        updateAllFiltersUI()
    }

    private fun setStates() {
        if (rights?.canReadCategoryOnFile == true) {
            categoriesTitle.isVisible = true
            chooseCategoriesFilter.isVisible = true
            belongToAllCategoriesFilter.isVisible = true
            belongToOneCategoryFilter.isVisible = true
        } else {
            categoriesTitle.isGone = true
            chooseCategoriesFilter.isGone = true
            belongToAllCategoriesFilter.isGone = true
            belongToOneCategoryFilter.isGone = true
        }
    }

    private fun setListeners() = with(searchFiltersViewModel) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        modificationDateFilter.setOnClickListener { safeNavigate(R.id.searchFilterDateDialog, bundleOf("date" to date)) }
        fileTypeFilter.setOnClickListener { safeNavigate(R.id.searchFilterFileTypeDialog, bundleOf("type" to type)) }

        belongToAllCategoriesFilter.setOnClickListener {
            categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
            updateCategoriesOwnershipUI()
        }
        belongToOneCategoryFilter.setOnClickListener {
            categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
            updateCategoriesOwnershipUI()
        }

        clearButton.setOnClickListener {
            clearFilters()
            updateAllFiltersUI()
        }

        saveButton.setOnClickListener {
            setBackNavigationResult(
                SEARCH_FILTERS_NAV_KEY, bundleOf(
                    SEARCH_FILTERS_DATE_BUNDLE_KEY to date,
                    SEARCH_FILTERS_TYPE_BUNDLE_KEY to type,
                    SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY to categories?.map { it.id }?.toIntArray(),
                    SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY to categoriesOwnership,
                )
            )
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Parcelable>(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_NAV_KEY) {
            searchFiltersViewModel.date = it as SearchDateFilter
            updateDateUI()
        }

        getBackNavigationResult<Parcelable>(SearchFilterFileTypeBottomSheetDialog.SEARCH_FILTER_TYPE_NAV_KEY) {
            searchFiltersViewModel.type = it as ConvertedType
            updateTypeUI()
        }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            searchFiltersViewModel.restoreCurrentFilters(requireContext())
            searchFiltersViewModel.categories = if (it.isNotEmpty()) {
                DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray())
            } else null
            updateAllFiltersUI()
        }
    }

    private fun updateAllFiltersUI() {
        updateDateUI()
        updateTypeUI()
        updateCategoriesUI()
        updateCategoriesOwnershipUI()

    }

    private fun updateDateUI() = with(modificationDateFilterText) {
        searchFiltersViewModel.date?.let { text = it.text }
            ?: run { setText(R.string.searchFiltersSelectDate) }
    }

    private fun updateTypeUI() {
        searchFiltersViewModel.type?.let {
            fileTypeFilterStartIcon.setImageResource(it.icon)
            fileTypeFilterText.setText(it.searchFilterName)
        } ?: run {
            fileTypeFilterStartIcon.setImageResource(R.drawable.ic_file)
            fileTypeFilterText.setText(R.string.searchFiltersSelectType)
        }
    }

    private fun updateCategoriesUI() {
        val categories = searchFiltersViewModel.categories ?: emptyList()
        categoriesContainer.setup(
            categories = categories,
            canPutCategoryOnFile = rights?.canPutCategoryOnFile ?: false,
            onClicked = {
                searchFiltersViewModel.backupCurrentFilters(requireContext())
                runCatching {
                    findNavController().navigate(
                        SearchFiltersFragmentDirections.actionSearchFiltersFragmentToSelectCategoriesFragment(
                            categories = categories.map { it.id }.toIntArray(),
                        )
                    )
                }
            }
        )
    }

    private fun updateCategoriesOwnershipUI() {
        if (searchFiltersViewModel.categoriesOwnership == CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES) {
            belongToOneCategoryFilter.setupSelection(false)
            belongToAllCategoriesFilter.setupSelection(true)
        } else {
            belongToAllCategoriesFilter.setupSelection(false)
            belongToOneCategoryFilter.setupSelection(true)
        }
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
