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
package com.infomaniak.drive.ui.fileList

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackSearchEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.SearchCategoriesOwnershipFilter
import com.infomaniak.drive.databinding.FragmentSearchFiltersBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterDateBottomSheetDialogArgs
import com.infomaniak.drive.ui.bottomSheetDialogs.SearchFilterTypeBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesUsageMode
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setMargins
import com.infomaniak.lib.core.utils.toPx

class SearchFiltersFragment : Fragment() {

    private var binding: FragmentSearchFiltersBinding by safeBinding()
    private val searchViewModel: SearchViewModel by navGraphViewModels(R.id.searchFragment)
    private val searchFiltersViewModel: SearchFiltersViewModel by navGraphViewModels(R.id.searchFiltersFragment)

    private val categoryRights = DriveInfosController.getCategoryRights()

    private val defaultTypeIconDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_file)
            ?.mutate()
            ?.apply { setTint(ResourcesCompat.getColor(resources, R.color.iconColor, null)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSearchFiltersBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeFilters()
        handleCategoryRights()
        setToolbar()
        setDateAndTypeFilters()
        setCategoriesOwnershipFilters()
        setClearButton()
        setSaveButton()
        listenToFiltersUpdates()

        binding.root.enableEdgeToEdge(withPadding = true, withBottom = false) { insets ->
            binding.saveButton.setMargins(bottom = insets.bottom)
        }
    }

    private fun initializeFilters() = with(searchFiltersViewModel) {
        if (useInitialValues) {
            if (date.value == null) date.value = searchViewModel.dateFilter
            if (type.value == null) type.value = searchViewModel.typeFilter
            if (categories == null) categories = searchViewModel.categoriesFilter
            if (categoriesOwnership == null) categoriesOwnership = searchViewModel.categoriesOwnershipFilter
        }

        updateAllFiltersUi()
    }

    private fun handleCategoryRights() = with(binding) {
        categoriesTitle.isVisible = categoryRights.canReadOnFile
        chooseCategoriesFilter.isVisible = categoryRights.canReadOnFile
    }

    private fun setToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setDateAndTypeFilters() = with(searchFiltersViewModel) {
        binding.dateFilter.setOnClickListener {
            safeNavigate(R.id.searchFilterDateDialog, SearchFilterDateBottomSheetDialogArgs(date = date.value).toBundle())
        }
        binding.typeFilter.setOnClickListener {
            safeNavigate(R.id.searchFilterTypeDialog, SearchFilterTypeBottomSheetDialogArgs(type = type.value).toBundle())
        }
    }

    private fun setCategoriesOwnershipFilters() = with(binding) {
        fun update(categoriesOwnershipFilter: SearchCategoriesOwnershipFilter) {
            searchFiltersViewModel.categoriesOwnership = categoriesOwnershipFilter
            updateCategoriesOwnershipUi()
        }
        belongToAllCategoriesFilter.setOnClickListener { update(SearchCategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES) }
        belongToOneCategoryFilter.setOnClickListener { update(SearchCategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY) }
    }

    private fun setClearButton() {
        binding.clearButton.setOnClickListener {
            searchFiltersViewModel.clearFilters()
            updateAllFiltersUi()
        }
    }

    private fun setSaveButton() = with(searchFiltersViewModel) {
        binding.saveButton.setOnClickListener {
            date.value?.let { trackSearchEvent(MatomoName.FilterDate) }
            type.value?.let { trackSearchEvent(MatomoName.FilterFileType) }
            categories?.let { trackSearchEvent(MatomoName.FilterCategory) }

            searchViewModel.dateFilter = date.value
            searchViewModel.typeFilter = type.value
            searchViewModel.categoriesFilter = categories
            searchViewModel.categoriesOwnershipFilter = categoriesOwnership
            findNavController().popBackStack()
        }
    }

    private fun listenToFiltersUpdates() = with(searchFiltersViewModel) {
        date.observe(viewLifecycleOwner) { updateDateUi() }
        type.observe(viewLifecycleOwner) { updateTypeUi() }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            if (it.isEmpty()) {
                categories = null
                categoriesOwnership = null
            } else {
                categories = DriveInfosController.getCategoriesFromIds(AccountUtils.currentDriveId, it.toTypedArray())
                if (categoriesOwnership == null) categoriesOwnership = SearchCategoriesOwnershipFilter.BELONG_TO_ALL_CATEGORIES
            }
            updateCategoriesUi()
            updateCategoriesOwnershipUi()
        }
    }

    private fun updateAllFiltersUi() {
        updateDateUi()
        updateTypeUi()
        updateCategoriesUi()
        updateCategoriesOwnershipUi()
    }

    private fun updateDateUi() = with(binding.dateFilterText) {
        searchFiltersViewModel.date.value
            ?.let { text = it.text }
            ?: run { setText(R.string.searchFiltersSelectDate) }
    }

    private fun updateTypeUi() = with(binding) {
        searchFiltersViewModel.type.value?.let {
            typeFilterStartIcon.setImageResource(it.icon)
            typeFilterText.setText(it.searchFilterName)
        } ?: run {
            typeFilterStartIcon.setImageDrawable(defaultTypeIconDrawable)
            typeFilterText.setText(R.string.searchFiltersSelectType)
        }
    }

    private fun updateCategoriesUi() {
        val categories = searchFiltersViewModel.categories ?: emptyList()
        binding.categoriesContainer.setup(
            categories = categories,
            canPutCategoryOnFile = categoryRights.canPutOnFile,
            layoutInflater = layoutInflater,
            onClicked = {
                safeNavigate(
                    SearchFiltersFragmentDirections.actionSearchFiltersFragmentToSelectCategoriesFragment(
                        categories = categories.map { it.id }.toIntArray(),
                        categoriesUsageMode = CategoriesUsageMode.SELECTED_CATEGORIES,
                    )
                )
            },
        )
    }

    private fun updateCategoriesOwnershipUi() = with(binding) {
        val isVisible = searchFiltersViewModel.categories?.isNotEmpty() == true
        belongToAllCategoriesFilter.isVisible = isVisible
        belongToOneCategoryFilter.isVisible = isVisible

        if (isVisible) {
            val belongToOne =
                searchFiltersViewModel.categoriesOwnership == SearchCategoriesOwnershipFilter.BELONG_TO_ONE_CATEGORY
            belongToAllCategoriesFilter.setupSelection(!belongToOne)
            belongToOneCategoryFilter.setupSelection(belongToOne)
        }
    }

    private fun MaterialCardView.setupSelection(enabled: Boolean) {
        strokeWidth = if (enabled) 2.toPx() else 0
        invalidate()
    }
}
