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
package com.infomaniak.drive.ui.fileList.categories

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.fileList.categories.CreateOrEditCategoryAdapter.Companion.COLORS
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.core.utils.toDp
import kotlinx.android.synthetic.main.fragment_create_or_edit_category.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.max

class CreateOrEditCategoryFragment : Fragment() {

    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: CreateOrEditCategoryFragmentArgs by navArgs()

    private val colorsAdapter: CreateOrEditCategoryAdapter by lazy { CreateOrEditCategoryAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create_or_edit_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        configureAdapter()
        val isCreateCategory = categoryId == CREATE_CATEGORY_ID
        setData(isCreateCategory)
        setStates(isCreateCategory)
        setListeners()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configCategoriesLayoutManager()
    }

    private fun configureAdapter() {
        with(colorsAdapter) {
            selectedPosition = COLORS.indexOfFirst { it == navigationArgs.categoryColor }.let { if (it == -1) 0 else it }
            configCategoriesLayoutManager()
            categoriesRecyclerView.adapter = this
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    private fun configCategoriesLayoutManager() {
        val numCategoriesColumns = getNumColorsColumns()
        val gridLayoutManager = GridLayoutManager(context, numCategoriesColumns)
        categoriesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getNumColorsColumns(minColumns: Int = 1, expectedItemSize: Int = 52): Int {
        val screenWidth = requireActivity().getScreenSizeInDp().x
        val margins = resources.getDimensionPixelSize(R.dimen.marginStandardSmall).toDp() * 2
        return max(minColumns, (screenWidth - margins) / expectedItemSize)
    }

    private fun setData(isCreateCategory: Boolean) = with(navigationArgs) {
        appBarTitle.title = getString(if (isCreateCategory) R.string.createCategoryTitle else R.string.editCategoryTitle)
        categoryNameValueInput.setText(categoryName)
        saveButton.initProgress(this@CreateOrEditCategoryFragment)
    }

    private fun setStates(isCreateCategory: Boolean) = with(navigationArgs) {
        categoryNameValueLayout.isGone = categoryIsPredefined
        editCategoryWarning.isGone = isCreateCategory
        saveButton.isEnabled = categoryIsPredefined || !isCreateCategory || categoryName.isNotEmpty()
    }

    private fun setListeners() = with(navigationArgs) {
        categoryNameValueInput.addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        with(saveButton) {
            setOnClickListener {
                showProgress()
                if (categoryId == CREATE_CATEGORY_ID) createCategory() else editCategory(categoryId)
            }
        }
    }

    private fun createCategory() {
        val name = categoryNameValueInput.text.toString()
        val color = COLORS[colorsAdapter.selectedPosition]
        createOrEditCategoryViewModel.createCategory(navigationArgs.driveId, name, color)
            .observe(viewLifecycleOwner) { apiResponse ->
                with(apiResponse) {
                    if (isSuccess()) {
                        data?.id?.let { addCategory(it, name, color) }
                    } else {
                        saveButton.hideProgress(R.string.buttonSave)
                        Utils.showSnackbar(requireView(), translateError())
                    }
                }
            }
    }

    private fun addCategory(categoryId: Int, name: String, color: String) {
        selectCategoriesViewModel.addCategory(navigationArgs.fileId, navigationArgs.driveId, categoryId)
            .observe(viewLifecycleOwner) { apiResponse ->
                with(apiResponse) {
                    if (isSuccess()) {
                        findNavController().popBackStack()
                    } else {
                        saveButton.hideProgress(R.string.buttonSave)
                        Utils.showSnackbar(requireView(), translateError())
                    }
                }
            }
    }

    private fun editCategory(categoryId: Int) {
        val name = categoryNameValueInput.text.toString().let { if (it.isEmpty()) null else it }
        val color = COLORS[colorsAdapter.selectedPosition]
        val driveId = navigationArgs.driveId
        createOrEditCategoryViewModel.editCategory(driveId, categoryId, name, color).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    Utils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    internal class CreateOrEditCategoryViewModel : ViewModel() {

        private var createCategoryJob = Job()
        private var editCategoryJob = Job()

        fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> {
            createCategoryJob.cancel()
            createCategoryJob = Job()
            return liveData(Dispatchers.IO + createCategoryJob) {
                with(ApiRepository.createCategory(driveId, name, color)) {
                    if (isSuccess()) DriveInfosController.updateDrive { it.categories.add(data) }
                    emit(this)
                }
            }
        }

        fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): LiveData<ApiResponse<Category>> {
            editCategoryJob.cancel()
            editCategoryJob = Job()
            return liveData(Dispatchers.IO + editCategoryJob) {
                with(ApiRepository.editCategory(driveId, categoryId, name, color)) {
                    if (isSuccess()) {
                        DriveInfosController.updateDrive { localDrive ->
                            val category = localDrive.categories.find { it.id == categoryId }
                            localDrive.categories.remove(category)
                            localDrive.categories.add(data)
                        }
                    }
                    emit(this)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            createCategoryJob.cancel()
            editCategoryJob.cancel()
        }
    }

    companion object {
        const val CREATE_CATEGORY_ID = -1
    }
}
