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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.CategoriesOwnershipFilter
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.SearchDateFilter
import com.infomaniak.drive.data.models.SearchDateFilter.DateFilterKey
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterDateBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterTypeBottomSheetDialog
import com.infomaniak.drive.ui.fileList.SearchFiltersViewModel.Companion.DEFAULT_CATEGORIES_OWNERSHIP_VALUE
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_search_filters.*
import java.util.*
import androidx.core.util.Pair as AndroidPair

class SearchFiltersFragment : Fragment() {

    private val searchFiltersViewModel: SearchFiltersViewModel by viewModels()
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
        setBackActionHandlers()
    }

    private fun initializeFilters() = with(searchFiltersViewModel) {
        if (date == null) date = navigationArgs.date
        if (type == null) type = navigationArgs.type?.let(ConvertedType::valueOf)
        if (categories == null) {
            categories = navigationArgs.categories?.toTypedArray()?.let(DriveInfosController::getCurrentDriveCategoriesFromIds)
        }
        if (categoriesOwnership == DEFAULT_CATEGORIES_OWNERSHIP_VALUE) {
            categoriesOwnership = navigationArgs.categoriesOwnership ?: DEFAULT_CATEGORIES_OWNERSHIP_VALUE
        }
        updateAllFiltersUI()
    }

    private fun handleRights() {
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

    private fun setToolbar() {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setDateAndTypeFilters() = with(searchFiltersViewModel) {
        dateFilter.setOnClickListener { safeNavigate(R.id.searchFilterDateDialog, bundleOf("date" to date)) }
        typeFilter.setOnClickListener { safeNavigate(R.id.searchFilterTypeDialog, bundleOf("type" to type)) }
    }

    private fun setCategoriesOwnershipFilters() = with(searchFiltersViewModel) {
        belongToAllCategoriesFilter.setOnClickListener {
            categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
            updateCategoriesOwnershipUI()
        }
        belongToOneCategoryFilter.setOnClickListener {
            categoriesOwnership = CategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
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
                    SEARCH_FILTERS_DATE_BUNDLE_KEY to date,
                    SEARCH_FILTERS_TYPE_BUNDLE_KEY to type,
                    SEARCH_FILTERS_CATEGORIES_BUNDLE_KEY to categories?.map { it.id }?.toIntArray(),
                    SEARCH_FILTERS_CATEGORIES_OWNERSHIP_BUNDLE_KEY to categoriesOwnership,
                )
            )
        }
    }

    private fun setBackActionHandlers() = with(searchFiltersViewModel) {
        getBackNavigationResult<Bundle>(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_NAV_KEY) {
            val key: DateFilterKey? = it.getParcelable(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_KEY_BUNDLE_KEY)
            if (key == DateFilterKey.CUSTOM) {
                handleCustomSearchDateFilter()
            } else {
                handleGenericSearchDateFilter(it)
            }
        }

        getBackNavigationResult<Parcelable>(SearchFilterTypeBottomSheetDialog.SEARCH_FILTER_TYPE_NAV_KEY) {
            type = it as ConvertedType
            updateTypeUI()
        }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            categories = if (it.isEmpty()) null else DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray())
            updateAllFiltersUI()
        }
    }

    private fun handleCustomSearchDateFilter() {
        showDateRangePicker { startTime, endTime ->
            val key = DateFilterKey.CUSTOM
            val start = Date(startTime).startOfTheDay()
            val end = Date(endTime).endOfTheDay()
            val text = start.intervalAsText(end)
            updateSearchDateFilter(key, start, end, text)
        }
    }

    private fun handleGenericSearchDateFilter(bundle: Bundle) = with(bundle) {
        val key = getParcelable<DateFilterKey>(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_KEY_BUNDLE_KEY) ?: return
        val start = getLong(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_START_BUNDLE_KEY)
        val end = getLong(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_END_BUNDLE_KEY)
        val text = getString(SearchFilterDateBottomSheetDialog.SEARCH_FILTER_DATE_TEXT_BUNDLE_KEY) ?: return
        updateSearchDateFilter(key, Date(start), Date(end), text)
    }

    private fun updateSearchDateFilter(key: DateFilterKey, start: Date, end: Date, text: String) {
        searchFiltersViewModel.date = SearchDateFilter(key, start, end, text)
        updateDateUI()
    }

    private fun showDateRangePicker(onPositiveButtonClicked: (Long, Long) -> Unit) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            with(dateRangePicker()) {
                addOnNegativeButtonClickListener { dismiss() }
                addOnPositiveButtonClickListener { onPositiveButtonClicked(it.first, it.second) }
                show(fragmentManager, toString())
            }
        }
    }

    private fun dateRangePicker(): MaterialDatePicker<AndroidPair<Long, Long>> {
        return MaterialDatePicker.Builder
            .dateRangePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setCalendarConstraints(constraintsUntilNow())
            .build()
    }

    private fun constraintsUntilNow(): CalendarConstraints {
        return CalendarConstraints.Builder()
            .setEnd(Date().time)
            .setValidator(DateValidatorPointBackward.now())
            .build()
    }

    private fun updateAllFiltersUI() {
        updateDateUI()
        updateTypeUI()
        updateCategoriesUI()
        updateCategoriesOwnershipUI()
    }

    private fun updateDateUI() = with(dateFilterText) {
        searchFiltersViewModel.date?.let { text = it.text }
            ?: run { setText(R.string.searchFiltersSelectDate) }
    }

    private fun updateTypeUI() {
        searchFiltersViewModel.type?.let {
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

    private fun updateCategoriesOwnershipUI() {
        if (searchFiltersViewModel.categoriesOwnership == CategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES) {
            belongToAllCategoriesFilter.setupSelection(true)
            belongToOneCategoryFilter.setupSelection(false)
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
