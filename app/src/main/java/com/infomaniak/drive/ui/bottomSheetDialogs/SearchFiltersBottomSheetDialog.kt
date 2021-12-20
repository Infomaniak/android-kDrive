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
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.CategoriesOwnershipFilter
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.utils.getBackNavigationResult
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_search_filters.*

class SearchFiltersBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val searchFiltersViewModel: SearchFiltersViewModel by viewModels()
    private val navigationArgs: SearchFiltersBottomSheetDialogArgs by navArgs()

    private val rights = DriveInfosController.getCategoryRights()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_search_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        configureFilters()
        configureClearButton()
        configureSaveButton()
        setupBackActionHandler()
    }

    private fun configureFilters() {
        configureDateUI()
        configureTypeUI()
        configureCategoriesUI()
        configureCategoriesOwnershipUI()
    }

    private fun configureClearButton() {
        clearButton.setOnClickListener {
            searchFiltersViewModel.clearFilters()
            updateDateUI()
            updateTypeUI()
            updateCategoriesUI()
            updateCategoriesOwnershipUI()
        }
    }

    private fun configureSaveButton() {
        saveButton.setOnClickListener {
            with(searchFiltersViewModel) {
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
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Parcelable>(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_NAV_KEY) {
            searchFiltersViewModel.date = it as SearchDateFilter
            updateDateUI()
        }

        getBackNavigationResult<Parcelable>(SearchFilterFileTypeBottomSheetDialog.SEARCH_FILTER_TYPE_NAV_KEY) {
            searchFiltersViewModel.type = it as ConvertedType
            updateTypeUI()
        }

        getBackNavigationResult<Bundle>(SelectCategoriesBottomSheetDialog.SELECT_CATEGORIES_NAV_KEY) { bundle ->
            val ids = bundle.getIntArray(SelectCategoriesBottomSheetDialog.CATEGORIES_BUNDLE_KEY)?.toTypedArray()
            searchFiltersViewModel.categories = if (ids?.isNotEmpty() == true) {
                DriveInfosController.getCurrentDriveCategoriesFromIds(ids)
            } else null
            updateCategoriesUI()
        }
    }

    private fun configureDateUI() {
        searchFiltersViewModel.date = navigationArgs.date
        updateDateUI()
        modificationDateFilter.setOnClickListener {
            safeNavigate(R.id.searchFilterDateDialog, bundleOf("date" to searchFiltersViewModel.date))
        }
    }

    private fun configureTypeUI() {
        searchFiltersViewModel.type = navigationArgs.type?.let { ConvertedType.valueOf(it) }
        updateTypeUI()
        fileTypeFilter.setOnClickListener {
            safeNavigate(R.id.searchFilterFileTypeDialog, bundleOf("type" to searchFiltersViewModel.type))
        }
    }

    private fun configureCategoriesUI() {
        searchFiltersViewModel.categories =
            navigationArgs.categories?.let { DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray()) }
        updateCategoriesUI()
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

    private fun configureCategoriesOwnershipUI() {
        with(searchFiltersViewModel) {
            categoriesOwnership = navigationArgs.categoriesOwnership ?: SearchFiltersViewModel.DEFAULT_CATEGORIES_OWNERSHIP_VALUE
            updateCategoriesOwnershipUI()
            belongToAllCategoriesFilter.setOnClickListener {
                categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
                updateCategoriesOwnershipUI()
            }
            belongToOneCategoryFilter.setOnClickListener {
                categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
                updateCategoriesOwnershipUI()
            }
        }
    }

    private fun updateDateUI() {
        with(modificationDateFilterText) {
            searchFiltersViewModel.date?.let { text = it.text }
                ?: run { setText(R.string.searchFiltersSelectDate) }
        }
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
                try {
                    findNavController().navigate(
                        SearchFiltersBottomSheetDialogDirections.actionSearchFiltersBottomSheetDialogToSelectCategoriesBottomSheetDialog(
                            fileId = -1,
                            categories = categories.map { it.id }.toIntArray(),
                        )
                    )
                } catch (_: IllegalArgumentException) {
                    // No-op
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
