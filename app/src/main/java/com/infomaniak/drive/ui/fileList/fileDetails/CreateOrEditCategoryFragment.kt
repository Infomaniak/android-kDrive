/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.infomaniak.drive.MatomoDrive.trackCategoriesEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.fileDetails.CreateOrEditCategoryAdapter.Companion.COLORS
import com.infomaniak.drive.utils.find
import com.infomaniak.drive.utils.getScreenSizeInDp
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import kotlinx.android.synthetic.main.fragment_create_or_edit_category.*
import kotlinx.coroutines.Dispatchers
import kotlin.math.max

class CreateOrEditCategoryFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val navigationArgs: CreateOrEditCategoryFragmentArgs by navArgs()

    private val colorsAdapter: CreateOrEditCategoryAdapter by lazy { CreateOrEditCategoryAdapter() }
    private val driveId: Int
        get() = files.first().driveId

    private lateinit var files: List<File>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create_or_edit_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        files = filesId?.map { fileId -> FileController.getFileById(fileId) }?.filterNotNull() ?: listOf()
        if (files.isEmpty()) {
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
        }
    }

    private fun setSaveButton(isCreateCategory: Boolean) = with(navigationArgs) {
        saveButton.apply {
            initProgress(this@CreateOrEditCategoryFragment)
            isEnabled = categoryIsPredefined || !isCreateCategory || categoryName.isNotEmpty()
            setOnClickListener {
                showProgress()
                if (categoryId == CREATE_CATEGORY_ID) {
                    trackCategoriesEvent("add")
                    createCategory()
                } else {
                    trackCategoriesEvent("edit")
                    editCategory(categoryId)
                }
            }
        }
    }

    private fun configCategoriesLayoutManager() {
        val numCategoriesColumns = getNumColorsColumns()
        val gridLayoutManager = GridLayoutManager(context, numCategoriesColumns)
        categoriesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getNumColorsColumns(): Int {
        val minNumberOfColumns = 1
        val screenWidth = requireActivity().getScreenSizeInDp().x
        val margins = resources.getDimensionPixelSize(R.dimen.marginStandardSmall).toDp() * 2
        val expectedItemSize = resources.getDimensionPixelSize(R.dimen.coloredChipSize).toDp()
        return max(minNumberOfColumns, (screenWidth - margins) / expectedItemSize)
    }

    private fun createCategory() {
        createOrEditCategoryViewModel.createCategory(
            driveId = driveId,
            name = categoryNameValueInput.text.toString(),
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    data?.id?.let(::addCategory)
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    private fun addCategory(categoryId: Int) {
        mainViewModel.manageCategory(driveId, categoryId, files, true).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    private fun editCategory(categoryId: Int) {
        createOrEditCategoryViewModel.editCategory(
            driveId = driveId,
            categoryId = categoryId,
            name = categoryNameValueInput.text.toString().let { it.ifEmpty { null } },
            color = COLORS[colorsAdapter.selectedPosition],
        ).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    findNavController().popBackStack()
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    SnackbarUtils.showSnackbar(requireView(), translateError())
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
