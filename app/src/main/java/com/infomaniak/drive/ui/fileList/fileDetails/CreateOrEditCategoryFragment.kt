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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.infomaniak.core.legacy.utils.SnackbarUtils
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.legacy.utils.toDp
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackCategoriesEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentCreateOrEditCategoryBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.fileDetails.CreateOrEditCategoryAdapter.Companion.COLORS
import com.infomaniak.drive.utils.getScreenSizeInDp
import kotlin.math.max

class CreateOrEditCategoryFragment : Fragment() {

    private var binding: FragmentCreateOrEditCategoryBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val navigationArgs: CreateOrEditCategoryFragmentArgs by navArgs()

    private val colorsAdapter: CreateOrEditCategoryAdapter by lazy { CreateOrEditCategoryAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentCreateOrEditCategoryBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setCategoryName()
        configureColorsAdapter()
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val isCreateCategory = navigationArgs.categoryId == CREATE_CATEGORY_ID
        appBarTitle.title = getString(if (isCreateCategory) R.string.createCategoryTitle else R.string.editCategoryTitle)
        editCategoryWarning.isGone = isCreateCategory
        setSaveButton(isCreateCategory)

        createOrEditCategoryViewModel.init(navigationArgs.filesIds).observe(viewLifecycleOwner) { hasNoFiles ->
            if (hasNoFiles) findNavController().popBackStack()
        }

        binding.root.enableEdgeToEdge()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configCategoriesLayoutManager()
    }

    private fun setCategoryName() = with(binding) {
        categoryNameValueInput.apply {
            setText(navigationArgs.categoryName)
            addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }
        }
        categoryNameValueLayout.isGone = navigationArgs.categoryIsPredefined
    }

    private fun configureColorsAdapter() {
        colorsAdapter.apply {
            selectedPosition = COLORS.indexOfFirst { it == navigationArgs.categoryColor }.let { if (it == -1) 0 else it }
            configCategoriesLayoutManager()
            binding.categoriesRecyclerView.adapter = this
        }
    }

    private fun setSaveButton(isCreateCategory: Boolean) = with(navigationArgs) {
        binding.saveButton.apply {
            initProgress(this@CreateOrEditCategoryFragment)
            isEnabled = categoryIsPredefined || !isCreateCategory || categoryName.isNotEmpty()
            setOnClickListener {
                showProgressCatching()
                if (categoryId == CREATE_CATEGORY_ID) {
                    trackCategoriesEvent(MatomoName.Add)
                    createCategory()
                } else {
                    trackCategoriesEvent(MatomoName.Edit)
                    editCategory(categoryId)
                }
            }
        }
    }

    private fun configCategoriesLayoutManager() {
        val numCategoriesColumns = getNumColorsColumns()
        val gridLayoutManager = GridLayoutManager(context, numCategoriesColumns)
        binding.categoriesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getNumColorsColumns(): Int {
        val minNumberOfColumns = 1
        val screenWidth = requireActivity().getScreenSizeInDp().x
        val margins = resources.getDimensionPixelSize(R.dimen.marginStandardSmall).toDp() * 2
        val expectedItemSize = resources.getDimensionPixelSize(R.dimen.coloredChipSize).toDp()

        return max(minNumberOfColumns, (screenWidth - margins) / expectedItemSize)
    }

    private fun createCategory() = with(binding) {
        createOrEditCategoryViewModel.createCategory(
            name = categoryNameValueInput.text.toString(),
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    data?.id?.let(::addCategory)
                } else {
                    saveButton.hideProgressCatching(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    private fun addCategory(categoryId: Int) {
        mainViewModel.manageCategory(
            categoryId = categoryId,
            files = createOrEditCategoryViewModel.selectedFiles,
            isAdding = true
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    binding.saveButton.hideProgressCatching(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    private fun editCategory(categoryId: Int) = with(binding) {
        createOrEditCategoryViewModel.editCategory(
            categoryId = categoryId,
            name = categoryNameValueInput.text.toString().let { it.ifEmpty { null } },
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    saveButton.hideProgressCatching(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    companion object {
        const val CREATE_CATEGORY_ID = -1
    }
}
