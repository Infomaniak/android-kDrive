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
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.fileList.fileDetails.CreateOrEditCategoryAdapter.Companion.COLORS
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.core.utils.toDp
import kotlinx.android.synthetic.main.fragment_create_or_edit_category.*
import kotlinx.coroutines.Dispatchers
import kotlin.math.max

class CreateOrEditCategoryFragment : Fragment() {

    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: CreateOrEditCategoryFragmentArgs by navArgs()

    private val colorsAdapter: CreateOrEditCategoryAdapter by lazy { CreateOrEditCategoryAdapter() }
    private lateinit var file: File

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create_or_edit_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        file = FileController.getFileById(fileId) ?: run {
            findNavController().popBackStack()
            return@with
        }

        setCategoryName()
        configureColorsAdapter()
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val isCreateCategory = categoryId == CREATE_CATEGORY_ID
        appBarTitle.title = getString(if (isCreateCategory) R.string.createCategoryTitle else R.string.editCategoryTitle)
        editCategoryWarning.isGone = isCreateCategory
        setSaveButton(isCreateCategory)
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id != R.id.createOrEditCategoryFragment) return
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configCategoriesLayoutManager()
    }

    private fun setCategoryName() = with(navigationArgs) {
        categoryNameValueInput.apply {
            setText(categoryName)
            addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }
        }
        categoryNameValueLayout.isGone = categoryIsPredefined
    }

    private fun configureColorsAdapter() {
        colorsAdapter.apply {
            selectedPosition = COLORS.indexOfFirst { it == navigationArgs.categoryColor }.let { if (it == -1) 0 else it }
            configCategoriesLayoutManager()
            categoriesRecyclerView.adapter = this
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    private fun setSaveButton(isCreateCategory: Boolean) = with(navigationArgs) {
        saveButton.apply {
            initProgress(this@CreateOrEditCategoryFragment)
            isEnabled = categoryIsPredefined || !isCreateCategory || categoryName.isNotEmpty()
            setOnClickListener {
                showProgress()
                if (categoryId == CREATE_CATEGORY_ID) createCategory() else editCategory(categoryId)
            }
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

    private fun createCategory() {
        createOrEditCategoryViewModel.createCategory(
            driveId = file.driveId,
            name = categoryNameValueInput.text.toString(),
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    data?.id?.let(::addCategory)
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    Utils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    private fun addCategory(categoryId: Int) {
        selectCategoriesViewModel.addCategory(file, categoryId)
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
        createOrEditCategoryViewModel.editCategory(
            driveId = file.driveId,
            categoryId = categoryId,
            name = categoryNameValueInput.text.toString().let { if (it.isEmpty()) null else it },
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
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

        fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> {
            return liveData(Dispatchers.IO) {
                with(ApiRepository.createCategory(driveId, name, color)) {
                    if (isSuccess()) DriveInfosController.updateDrive { it.categories.add(data) }
                    emit(this)
                }
            }
        }

        fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): LiveData<ApiResponse<Category>> {
            return liveData(Dispatchers.IO) {
                with(ApiRepository.editCategory(driveId, categoryId, name, color)) {
                    if (isSuccess()) {
                        DriveInfosController.updateDrive { localDrive ->
                            localDrive.categories.apply {
                                find(categoryId)?.deleteFromRealm()
                                add(data)
                            }
                        }
                    }
                    emit(this)
                }
            }
        }
    }

    companion object {
        const val CREATE_CATEGORY_ID = -1
    }
}
